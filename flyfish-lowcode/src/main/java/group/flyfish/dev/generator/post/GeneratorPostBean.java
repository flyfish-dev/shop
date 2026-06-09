package group.flyfish.dev.generator.post;

import group.flyfish.dev.generator.handlers.GeneratorHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;

@RequiredArgsConstructor
@Slf4j
public class GeneratorPostBean implements ApplicationListener<ApplicationStartedEvent> {

    private final GeneratorHandler generatorHandler;

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        log.info("正在生成代码，请稍候...");
        generatorHandler.generate();
        log.info("代码生成完毕，已弹出，后程序稍后将关闭...");
//        SpringApplication.exit(event.getApplicationContext());
    }
}
