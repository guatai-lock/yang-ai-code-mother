package com.guatai.yangaicodemother.event;

import com.guatai.yangaicodemother.model.entity.App;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 应用删除事件
 * 
 * 说明：只传递删除时需要的字段，避免冗余
 */
@Getter
public class AppDeletedEvent extends ApplicationEvent {
    
    private final Long appId;
    private final String archivePath;  // 下线时保存的归档路径
    private final String codeGenType;  // 代码生成类型
    
    public AppDeletedEvent(Object source, App app) {
        super(source);
        this.appId = app.getId();
        this.archivePath = app.getArchivePath();
        this.codeGenType = app.getCodeGenType();
    }
}
