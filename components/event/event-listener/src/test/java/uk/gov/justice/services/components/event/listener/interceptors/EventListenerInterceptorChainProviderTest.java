package uk.gov.justice.services.components.event.listener.interceptors;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static uk.gov.justice.services.core.annotation.Component.EVENT_LISTENER;

import uk.gov.justice.services.core.accesscontrol.LocalAccessControlInterceptor;
import uk.gov.justice.services.core.audit.LocalAuditInterceptor;
import uk.gov.justice.services.core.interceptor.Interceptor;
import uk.gov.justice.services.metrics.interceptor.IndividualActionMetricsInterceptor;
import uk.gov.justice.services.metrics.interceptor.TotalActionMetricsInterceptor;

import java.util.List;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

public class EventListenerInterceptorChainProviderTest {

    @Test
    public void shouldReturnComponent() throws Exception {
        assertThat(new EventListenerInterceptorChainProvider().component(), is(EVENT_LISTENER));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldProvideDefaultInterceptorChainTypes() throws Exception {
        final List<Pair<Integer, Class<? extends Interceptor>>> interceptorChainTypes = new EventListenerInterceptorChainProvider().interceptorChainTypes();

        assertThat(interceptorChainTypes, containsInAnyOrder(
                new ImmutablePair<>(1, TotalActionMetricsInterceptor.class),
                new ImmutablePair<>(2, IndividualActionMetricsInterceptor.class),
                new ImmutablePair<>(3000, LocalAuditInterceptor.class),
                new ImmutablePair<>(1000, EventBufferInterceptor.class),
                new ImmutablePair<>(2000, EventFilterInterceptor.class)));
    }
}