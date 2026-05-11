package tech.ynfy.frame.config.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 〈〉
 *
 * @author Hsiong
 * @version 1.0.0
 * @since 1/13/26
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {
	
	@Override
	public void insertFill(MetaObject metaObject) {
		// 无需填入 - createAt 使用 sql 默认值
	}
	
	@Override
	public void updateFill(MetaObject metaObject) {
		// 更新时自动刷新更新时间, 与 `@TableField(value = "update_at", fill = FieldFill.UPDATE)` 搭配使用
		this.strictUpdateFill(metaObject, "updateAt", Date.class, new Date());
	}
	
}
