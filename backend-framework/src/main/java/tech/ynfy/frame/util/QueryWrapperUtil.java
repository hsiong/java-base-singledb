package tech.ynfy.frame.util;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import tech.ynfy.frame.annotation.SqlUtilProperty;

import java.lang.reflect.Field;
import java.util.Collection;

/**
 * MyBatis-Plus 动态查询构建工具类
 * 根据DTO对象自动构建QueryWrapper，支持多种查询类型
 *
 * @author Hsiong
 * @since 2025-01-08
 */
public class QueryWrapperUtil {
	
	/**
	 * 根据 DTO 对象动态构建 QueryWrapper
	 * 支持通过反射自动识别字段并构建查询条件
	 *
	 * @param queryDTO 查询条件 DTO 对象
	 * @param <T>      实体类型
	 * @return 构建好的 QueryWrapper
	 */
	public static <T> QueryWrapper<T> buildQueryWrapper(Object queryDTO) {
		QueryWrapper<T> wrapper = new QueryWrapper<>();
		
		if (ObjectUtil.isEmpty(queryDTO)) {
			return wrapper;
		}
		
		Class<?> clazz = queryDTO.getClass();
		Field[] fields = clazz.getDeclaredFields();
		
		for (Field field : fields) {
			field.setAccessible(true);
			String fieldName = field.getName();
			
			// 跳过序列化字段和分页字段
			if ("serialVersionUID".equals(fieldName) || "page".equals(fieldName) ||
				"rows".equals(fieldName)) {
				continue;
			}
			
			// 检查是否有注解
			SqlUtilProperty annotation = field.getAnnotation(SqlUtilProperty.class);
			
			// 如果标记为忽略，跳过该字段
			if (annotation != null && annotation.ignore()) {
				continue;
			}
			
			try {
				Object fieldValue = field.get(queryDTO);
				
				// 空值跳过（除了 IS_NULL 和 IS_NOT_NULL）
				if (ObjectUtil.isEmpty(fieldValue) && (annotation == null || (annotation.type() !=
																			  SqlUtilProperty.QueryType.IS_NULL &&
																			  annotation.type() !=
																			  SqlUtilProperty.QueryType.IS_NOT_NULL))) {
					continue;
				}
				
				// 获取数据库字段名
				String columnName = getColumnName(field, annotation);
				
				// 获取查询类型
				SqlUtilProperty.QueryType queryType = getQueryType(field, annotation);
				
				// 根据查询类型构建条件
				applyCondition(wrapper, columnName, fieldValue, queryType);
				
			} catch (IllegalAccessException e) {
				throw new RuntimeException("访问字段失败: " + fieldName, e);
			}
		}
		
		return wrapper;
	}
	
	/**
	 * 获取数据库字段名
	 */
	private static String getColumnName(Field field, SqlUtilProperty annotation) {
		if (annotation != null && StrUtil.isNotBlank(annotation.column())) {
			return annotation.column();
		}
		// 默认将驼峰转下划线
		return camelToUnderline(field.getName());
	}
	
	/**
	 * 获取查询类型
	 */
	private static SqlUtilProperty.QueryType getQueryType(Field field, SqlUtilProperty annotation) {
		if (annotation != null) {
			return annotation.type();
		}
		// 默认：字符串类型使用LIKE，其他类型使用EQ
		if (field.getType() == String.class) {
			return SqlUtilProperty.QueryType.LIKE;
		}
		return SqlUtilProperty.QueryType.EQ;
	}
	
	/**
	 * 应用查询条件
	 */
	private static <T> void applyCondition(QueryWrapper<T> wrapper,
										   String columnName,
										   Object value,
										   SqlUtilProperty.QueryType queryType) {
		switch (queryType) {
			case EQ:
				wrapper.eq(columnName, value);
				break;
			case NE:
				wrapper.ne(columnName, value);
				break;
			case GT:
				wrapper.gt(columnName, value);
				break;
			case GE:
				wrapper.ge(columnName, value);
				break;
			case LT:
				wrapper.lt(columnName, value);
				break;
			case LE:
				wrapper.le(columnName, value);
				break;
			case LIKE:
				wrapper.like(columnName, value);
				break;
			case LIKE_LEFT:
				wrapper.likeLeft(columnName, value);
				break;
			case LIKE_RIGHT:
				wrapper.likeRight(columnName, value);
				break;
			case IN:
				if (value instanceof Collection) {
					wrapper.in(columnName, (Collection<?>) value);
				} else if (value.getClass().isArray()) {
					wrapper.in(columnName, (Object[]) value);
				}
				break;
			case NOT_IN:
				if (value instanceof Collection) {
					wrapper.notIn(columnName, (Collection<?>) value);
				} else if (value.getClass().isArray()) {
					wrapper.notIn(columnName, (Object[]) value);
				}
				break;
			case IS_NULL:
				wrapper.isNull(columnName);
				break;
			case IS_NOT_NULL:
				wrapper.isNotNull(columnName);
				break;
			default:
				wrapper.eq(columnName, value);
		}
	}
	
	/**
	 * 将驼峰命名转换为下划线命名
	 *
	 * @param camelCase 驼峰命名字符串
	 * @return 下划线命名字符串
	 */
	private static String camelToUnderline(String camelCase) {
		if (camelCase == null || camelCase.length() < 2) {
			return camelCase;
		}
		
		StringBuilder result = new StringBuilder();
		result.append(camelCase.charAt(0));
		
		for (int i = 1; i < camelCase.length(); i++) {
			char ch = camelCase.charAt(i);
			if (Character.isUpperCase(ch)) {
				result.append('_');
				result.append(Character.toLowerCase(ch));
			} else {
				result.append(ch);
			}
		}
		
		return result.toString();
	}
	
	/**
	 * 将下划线命名转换为驼峰命名
	 *
	 * @param underline 下划线命名字符串
	 * @return 驼峰命名字符串
	 */
	private static String underlineToCamel(String underline) {
		if (underline == null || underline.length() == 0) {
			return underline;
		}
		
		StringBuilder result = new StringBuilder();
		boolean nextUpperCase = false;
		
		for (int i = 0; i < underline.length(); i++) {
			char ch = underline.charAt(i);
			if (ch == '_') {
				nextUpperCase = true;
			} else {
				if (nextUpperCase) {
					result.append(Character.toUpperCase(ch));
					nextUpperCase = false;
				} else {
					result.append(Character.toLowerCase(ch));
				}
			}
		}
		
		return result.toString();
	}
}
