package org.apache.skywalking.apm.plugin.tomcat78x;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

public class ForwardInterceptor implements InstanceMethodsAroundInterceptor, InstanceConstructorInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        if (ContextManager.isActive()) {
            AbstractSpan abstractTracingSpan = ContextManager.activeSpan();
            Map<String, String> eventMap = new HashMap<String, String>();
            eventMap.put("forward-url", objInst.getSkyWalkingDynamicField() == null ? "" : String.valueOf(objInst.getSkyWalkingDynamicField()));
            abstractTracingSpan.log(System.currentTimeMillis(), eventMap);
            ContextManager.getRuntimeContext().put(Constants.FORWARD_REQUEST_FLAG, true);
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        ContextManager.getRuntimeContext().remove(Constants.FORWARD_REQUEST_FLAG);
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {

    }

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        objInst.setSkyWalkingDynamicField(allArguments[1]);
    }
}
