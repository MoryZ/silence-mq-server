package com.old.silence.job.server.listener;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import com.old.silence.job.log.SilenceJobLog;
import com.old.silence.job.server.common.Lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 关闭监听器
 *
 */
@Component
public class EndListener implements ApplicationListener<ContextClosedEvent> {

    private final List<Lifecycle> lifecycles= new ArrayList<>();

    public EndListener(ObjectProvider<Lifecycle> objectProvider) {
        var lifecycleList = objectProvider.stream().collect(Collectors.toList());
        lifecycles.addAll(lifecycleList);
    }

    @Override
    public void onApplicationEvent(@NonNull ContextClosedEvent event) {
        SilenceJobLog.LOCAL.info("silence-job client about to shutdown v{}", "V1.8");
        lifecycles.forEach(Lifecycle::close);
        SilenceJobLog.LOCAL.info("silence-job client closed successfully v{}", "V1.8");
    }
}
