package tech.ynfy.frame.module;


import com.alibaba.fastjson.annotation.JSONField;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * @Description: Entity基类
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class BaseEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/** ID */
	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "ID")
	private String id;
	
	/**
	 * 创建时间
	 */
	@Schema(description = "创建时间")
	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@JSONField(format = "yyyy-MM-dd HH:mm:ss")
	@JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
	private Date createAt;
	
	/**
	 * 更新时间
	 */
	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
	@JSONField(format = "yyyy-MM-dd HH:mm:ss")
	@Schema(description = "更新时间")
	@TableField(value = "update_at", fill = FieldFill.UPDATE)
	private Date updateAt;

	/** 创建人 */
	@Schema(description = "创建人")
	private String createBy;

	/**更新人*/
	@Schema(description = "更新人")
	private String updateBy;
}
