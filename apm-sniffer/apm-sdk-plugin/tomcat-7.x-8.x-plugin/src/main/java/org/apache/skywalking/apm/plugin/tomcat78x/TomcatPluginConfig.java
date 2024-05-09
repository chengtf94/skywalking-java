package org.apache.skywalking.apm.plugin.tomcat78x;

import org.apache.skywalking.apm.agent.core.boot.PluginConfig;

public class TomcatPluginConfig {
    public static class Plugin {
        @PluginConfig(root = TomcatPluginConfig.class)
        public static class Tomcat {
            /**
             * This config item controls that whether the Tomcat plugin should collect the parameters of the request.
             */
            public static boolean COLLECT_HTTP_PARAMS = false;
        }

        @PluginConfig(root = TomcatPluginConfig.class)
        public static class Http {
            /**
             * When either {@link Tomcat#COLLECT_HTTP_PARAMS} is enabled, how many characters to keep and send to the
             * OAP backend, use negative values to keep and send the complete parameters, NB. this config item is added
             * for the sake of performance
             */
            public static int HTTP_PARAMS_LENGTH_THRESHOLD = 1024;
        }
    }
}
