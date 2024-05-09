package org.apache.skywalking.apm.plugin.tomcat78x;

import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.catalina.connector.Request;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.util.CollectionUtil;
import org.apache.skywalking.apm.agent.core.util.MethodUtil;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.tomcat.util.http.Parameters;

/**
 * {@link TomcatInvokeInterceptor} fetch the serialized context data by using {@link
 * HttpServletRequest#getHeader(String)}. The {@link TraceSegment#ref} of current trace segment will reference to the
 * trace segment id of the previous level if the serialized context is not null.
 */
public class TomcatInvokeInterceptor implements InstanceMethodsAroundInterceptor {

    private static boolean IS_SERVLET_GET_STATUS_METHOD_EXIST;
    private static final String SERVLET_RESPONSE_CLASS = "javax.servlet.http.HttpServletResponse";
    private static final String GET_STATUS_METHOD = "getStatus";

    static {
        IS_SERVLET_GET_STATUS_METHOD_EXIST = MethodUtil.isMethodExist(
            TomcatInvokeInterceptor.class.getClassLoader(), SERVLET_RESPONSE_CLASS, GET_STATUS_METHOD);
    }

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        Request request = (Request) allArguments[0];
        ContextCarrier contextCarrier = new ContextCarrier();

        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            next.setHeadValue(request.getHeader(next.getHeadKey()));
        }
        String operationName =  String.join(":", request.getMethod(), request.getRequestURI());
        AbstractSpan span = ContextManager.createEntrySpan(operationName, contextCarrier);
        Tags.URL.set(span, request.getRequestURL().toString());
        Tags.HTTP.METHOD.set(span, request.getMethod());
        span.setComponent(ComponentsDefine.TOMCAT);
        SpanLayer.asHttp(span);

        if (TomcatPluginConfig.Plugin.Tomcat.COLLECT_HTTP_PARAMS) {
            collectHttpParam(request, span);
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        Request request = (Request) allArguments[0];
        HttpServletResponse response = (HttpServletResponse) allArguments[1];

        AbstractSpan span = ContextManager.activeSpan();
        if (IS_SERVLET_GET_STATUS_METHOD_EXIST) {
            Tags.HTTP_RESPONSE_STATUS_CODE.set(span, response.getStatus());
            if (response.getStatus() >= 400) {
                span.errorOccurred();
            }
        }
        // Active HTTP parameter collection automatically in the profiling context.
        if (!TomcatPluginConfig.Plugin.Tomcat.COLLECT_HTTP_PARAMS && span.isProfiling()) {
            collectHttpParam(request, span);
        }
        ContextManager.getRuntimeContext().remove(Constants.FORWARD_REQUEST_FLAG);
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        AbstractSpan span = ContextManager.activeSpan();
        span.log(t);
    }

    private void collectHttpParam(Request request, AbstractSpan span) {
        final Map<String, String[]> parameterMap = new HashMap<>();
        final org.apache.coyote.Request coyoteRequest = request.getCoyoteRequest();
        final Parameters parameters = coyoteRequest.getParameters();
        for (final Enumeration<String> names = parameters.getParameterNames(); names.hasMoreElements(); ) {
            final String name = names.nextElement();
            parameterMap.put(name, parameters.getParameterValues(name));
        }

        if (!parameterMap.isEmpty()) {
            String tagValue = CollectionUtil.toString(parameterMap);
            tagValue = TomcatPluginConfig.Plugin.Http.HTTP_PARAMS_LENGTH_THRESHOLD > 0 ?
                StringUtil.cut(tagValue, TomcatPluginConfig.Plugin.Http.HTTP_PARAMS_LENGTH_THRESHOLD) :
                tagValue;
            Tags.HTTP.PARAMS.set(span, tagValue);
        }
    }
}
