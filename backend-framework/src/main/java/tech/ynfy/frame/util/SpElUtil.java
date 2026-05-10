package tech.ynfy.frame.util;

import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
/**
 * 〈〉
 *
 * @author Hsiong
 * @version 1.0.0
 * @since 11/24/25
 */
public class SpElUtil {
	
	
	// 用于解析 SpEL 表达式的解析器
	private static final ExpressionParser PARSER = new SpelExpressionParser();
	
	// 用于获取方法参数名
	private static final DefaultParameterNameDiscoverer NAME_DISCOVERER =
		new DefaultParameterNameDiscoverer();
	
	/**
	 * 解析方法参数上的 SpEL 表达式
	 *
	 * @param method 当前方法
	 * @param args   方法参数
	 * @param spel   SpEL 表达式（如 "#id"、"#dto.userId"、"#p0"）
	 * @return 解析后的值
	 */
	public static Object parseSpEl(Method method, Object[] args, String spel) {
		if (method == null || args == null || spel == null) {
			return null;
		}
		
		// 创建 SpEL 上下文
		StandardEvaluationContext context = new StandardEvaluationContext();
		
		// 获取参数名
		String[] paramNames = NAME_DISCOVERER.getParameterNames(method);
		
		// 将参数放入 SpEL 上下文
		if (paramNames != null) {
			for (int i = 0; i < paramNames.length; i++) {
				// 1. 通过参数名访问：#id
				context.setVariable(paramNames[i], args[i]);
				
				// 2. 通过 #p0 访问第一个参数
				context.setVariable("p" + i, args[i]);
			}
		}
		
		// 解析表达式
		Expression expression = PARSER.parseExpression(spel);
		
		return expression.getValue(context);
	}
}
