package uk.gov.justice.services.test.utils.core.helper;

import static com.jayway.awaitility.Awaitility.await;
import static java.util.Optional.empty;
import static java.util.concurrent.TimeUnit.SECONDS;
import static javax.ws.rs.core.Response.Status.fromStatusCode;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import uk.gov.justice.services.test.utils.core.http.RequestParams;
import uk.gov.justice.services.test.utils.core.http.RequestParamsBuilder;
import uk.gov.justice.services.test.utils.core.http.ResponseData;
import uk.gov.justice.services.test.utils.core.rest.RestClient;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.core.Response;

import com.google.common.annotations.VisibleForTesting;
import com.jayway.awaitility.core.ConditionEvaluationLogger;
import com.jayway.awaitility.core.ConditionFactory;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

/**
 * Client for polling a rest endpoint and matching the response against the specified Matcher.
 */
public class PollingRestClientHelper {

    private final RestClient restClient;
    private final RequestParams requestParams;

    private ConditionFactory await;
    private Matcher<ResponseData> expectedResponseMatcher;
    private Optional<Matcher<ResponseData>> ignoredResponseMatcher = empty();

    @VisibleForTesting
    PollingRestClientHelper(final RestClient restClient, final RequestParams requestParams) {
        this.requestParams = requestParams;
        this.restClient = restClient;
        this.await = await().with().pollInterval(1, SECONDS).with().timeout(10, SECONDS);
    }

    public static PollingRestClientHelper poll(final RequestParams requestParams) {
        return new PollingRestClientHelper(new RestClient(), requestParams);
    }

    public static PollingRestClientHelper poll(final RequestParamsBuilder requestParamsBuilder) {
        return new PollingRestClientHelper(new RestClient(), requestParamsBuilder.build());
    }

    /**
     *
     * @param matchers
     * @return
     */
    public PollingRestClientHelper ignoring(final Matcher<ResponseData>... matchers) {
        if (ignoredResponseMatcher.isPresent()) {
            this.ignoredResponseMatcher = Optional.of(anyOf(ignoredResponseMatcher.get(), allOf(matchers)));
        } else {
            this.ignoredResponseMatcher = Optional.of(allOf(matchers));
        }
        return this;
    }

    /**
     *
     * @param matchers
     * @return
     */
    public ResponseData until(final Matcher<ResponseData>... matchers) {
        expectedResponseMatcher = allOf(matchers);

        final ResponseData responseData = this.await.until(new CallableRestClient(requestParams), combinedMatcher());

        assertThat(responseData, is(expectedResponseMatcher));

        return responseData;
    }

    /**
     * prints the matcher evaluation results, on every poll, to the console using System.out.printf.
     * It also prints the final value if applicable.
     *
     * @return PollingRestClientHelper
     */
    public PollingRestClientHelper logging() {
        this.await = this.await.with().conditionEvaluationListener(new ConditionEvaluationLogger());
        return this;
    }

    /**
     * Overrides the delay between polls. If not specified a default of 1 second is used.
     *
     * @param pollInterval the poll interval
     * @param unit         the unit
     * @return this
     */
    public PollingRestClientHelper pollInterval(final long pollInterval, final TimeUnit unit) {
        this.await = this.await.with().pollInterval(pollInterval, unit);
        return this;
    }

    /**
     * Poll at most timeout before throwing a timeout exception.
     *
     * Overrides the default timeout period. If not specified a default of 10 seconds is used.
     *
     * @param timeout the timeout
     * @param unit    the unit
     */
    public PollingRestClientHelper timeout(final long timeout, final TimeUnit unit) {
        this.await = this.await.with().timeout(timeout, unit);
        return this;
    }

    /**
     * Specify the delay that will be used before PollingRestClientHelper starts polling for the
     * result the first time. If you don't specify a poll delay explicitly it'll be the same as the
     * poll interval.
     *
     * @param delay the delay
     * @param unit  the unit
     */
    public PollingRestClientHelper pollDelay(final long delay, final TimeUnit unit) {
        this.await = this.await.with().pollDelay(delay, unit);
        return this;
    }

    /**
     * A method to increase the readability
     *
     * @return PollingRestClientHelper
     */
    public PollingRestClientHelper with() {
        return this;
    }

    /**
     * A method to increase the readability
     *
     * @return PollingRestClientHelper
     */
    public PollingRestClientHelper and() {
        return this;
    }


    private Matcher<ResponseData> combinedMatcher() {
        if (ignoredResponseMatcher.isPresent()) {
            return new StopPollingMatcher(expectedResponseMatcher, ignoredResponseMatcher.get());
        }
        return expectedResponseMatcher;
    }

    private class CallableRestClient implements Callable<ResponseData> {
        private final RequestParams requestParams;

        private CallableRestClient(final RequestParams requestParams) {
            this.requestParams = requestParams;
        }

        @Override
        public ResponseData call() throws Exception {
            final Response response = restClient.query(
                    requestParams.getUrl(),
                    requestParams.getMediaType(),
                    requestParams.getHeaders());

            return new ResponseData(fromStatusCode(response.getStatus()), response.readEntity(String.class));
        }
    }

    private class StopPollingMatcher extends TypeSafeDiagnosingMatcher<ResponseData> {

        private final Matcher<ResponseData> expectedResponseDataMatcher;
        private final Matcher<ResponseData> ignoredResponseDataMatcher;

        StopPollingMatcher(final Matcher<ResponseData> expectedResponseDataMatcher, final Matcher<ResponseData> ignoredResponseDataMatcher) {
            this.expectedResponseDataMatcher = expectedResponseDataMatcher;
            this.ignoredResponseDataMatcher = ignoredResponseDataMatcher;
        }

        @Override
        protected boolean matchesSafely(final ResponseData responseData, final Description description) {
            if (expectedResponseDataMatcher.matches(responseData)) {
                return true;
            } else if (ignoredResponseDataMatcher.matches(responseData)) {
                ignoredResponseDataMatcher.describeMismatch(responseData, description);
                return false;
            }

            return true;
        }

        @Override
        public void describeTo(final Description description) {
            description.appendDescriptionOf(ignoredResponseDataMatcher);
        }
    }


}
