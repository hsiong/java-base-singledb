package tech.ynfy.frame.config.mybatis;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.postgresql.util.PSQLException;

import java.sql.*;
import java.util.*;

@Intercepts({
	@Signature(
		type = Executor.class,
		method = "update",
		args = {MappedStatement.class, Object.class}
	)
})
public class VarcharLengthCheckInterceptor implements Interceptor {
	
	@Override
	public Object intercept(Invocation invocation) throws Throwable {
		try {
			// 正常执行 SQL
			return invocation.proceed();
		} catch (Throwable ex) {
			Throwable root = unwrap(ex);
			// 只处理 PostgreSQL 长度超限的情况
			if (root instanceof PSQLException &&
				root.getMessage() != null &&
				root.getMessage().contains("value too long")) {
				
				MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
				Object parameterObject = invocation.getArgs()[1];
				BoundSql boundSql = ms.getBoundSql(parameterObject);
				
				// String sql = boundSql.getSql().trim().toLowerCase(); // 只分析 INSERT（你也可以扩展到 UPDATE）
				
				try (Connection conn = ms.getConfiguration()
										 .getEnvironment()
										 .getDataSource()
										 .getConnection()) {
					
					SqlInfo sqlInfo = parseInsertSql(boundSql.getSql());
					if (sqlInfo != null) {
						Map<String, Integer> varcharLimitMap = loadColumnMaxLength(conn, sqlInfo);
						// 这里会根据列名 + 参数值，推算哪个字段超长并抛出更友好的异常
						checkParametersAndThrow(boundSql, sqlInfo, varcharLimitMap);
					}
				} catch (Exception ignore) {
					throw ignore;
					// 如果分析失败，就不要拦截原始异常
				}
			}
			// 分析不了或者不是这个错误，就把原始异常抛出去
			throw ex;
		}
	}
	
	@Override
	public Object plugin(Object target) {
		return Plugin.wrap(target, this);
	}
	
	@Override
	public void setProperties(Properties properties) {}
	
	/* ================= 工具方法：异常解包 ================= */
	
	private Throwable unwrap(Throwable t) {
		while (t.getCause() != null && t != t.getCause()) {
			t = t.getCause();
		}
		return t;
	}
	
	/* ================= 解析 INSERT SQL ================= */
	
	private static class SqlInfo {
		String schema;
		String tableName;
		List<String> columns = Collections.emptyList();
	}
	
	private SqlInfo parseInsertSql(String sql) {
		try {
			String lower = sql.toLowerCase();
			int intoIdx = lower.indexOf("into");
			if (intoIdx < 0) {
				return parseUpdateSql(sql);
			}
			
			int leftParenIdx = sql.indexOf("(", intoIdx);
			int rightParenIdx = sql.indexOf(")", leftParenIdx);
			if (leftParenIdx < 0 || rightParenIdx < 0) return null;
			
			String fullTable = sql.substring(intoIdx + 4, leftParenIdx).trim();
			fullTable = fullTable.replace("\"", "").replace("`", "");
			
			SqlInfo info = new SqlInfo();
			
			int dotIdx = fullTable.indexOf(".");
			if (dotIdx > 0 && dotIdx < fullTable.length() - 1) {
				info.schema = fullTable.substring(0, dotIdx).trim();
				info.tableName = fullTable.substring(dotIdx + 1).trim();
			} else {
				info.tableName = fullTable.trim();
			}
			
			String colsPart = sql.substring(leftParenIdx + 1, rightParenIdx);
			String[] colArr = colsPart.split(",");
			List<String> columns = new ArrayList<>();
			for (String col : colArr) {
				String c = col.trim().replace("\"", "").replace("`", "");
				if (!c.isEmpty()) columns.add(c);
			}
			info.columns = columns;
			return info;
		} catch (Exception e) {
			return null;
		}
	}
	
	private SqlInfo parseUpdateSql(String sql) {
		try {
			String lower = sql.toLowerCase();
			int updateIdx = lower.indexOf("update");
			int setIdx = lower.indexOf("set");
			
			if (updateIdx < 0 || setIdx < 0) return null;
			
			// 表名
			String fullTable = sql.substring(updateIdx + 6, setIdx).trim();
			fullTable = fullTable.replace("\"", "").replace("`", "");
			
			SqlInfo info = new SqlInfo();
			int dotIdx = fullTable.indexOf(".");
			if (dotIdx > 0 && dotIdx < fullTable.length() - 1) {
				info.schema = fullTable.substring(0, dotIdx).trim();
				info.tableName = fullTable.substring(dotIdx + 1).trim();
			} else {
				info.tableName = fullTable.trim();
			}
			
			// 截取 SET ... 和 WHERE 之间的部分
			int whereIdx = lower.indexOf("where");
			String setPart = (whereIdx > 0)
							 ? sql.substring(setIdx + 3, whereIdx)
							 : sql.substring(setIdx + 3);
			
			// 解析字段名称
			String[] assigns = setPart.split(",");
			List<String> columns = new ArrayList<>();
			for (String assign : assigns) {
				String[] kv = assign.split("=");
				if (kv.length > 0) {
					String col = kv[0].trim().replace("`", "").replace("\"", "");
					if (!col.isEmpty()) columns.add(col);
				}
			}
			info.columns = columns;
			
			return info;
			
		} catch (Exception e) {
			return null;
		}
	}
	
	
	/* ================= 读取表字段长度 ================= */
	
	private Map<String, Integer> loadColumnMaxLength(Connection conn, SqlInfo sqlInfo) throws SQLException {
		Map<String, Integer> map = new HashMap<>();
		
		String schema = sqlInfo.schema;
		if (schema == null || schema.isEmpty()) {
			try {
				schema = conn.getSchema(); // Postgres 一般为 "public"
			} catch (SQLFeatureNotSupportedException ignore) {}
		}
		
		DatabaseMetaData meta = conn.getMetaData();
		try (ResultSet rs = meta.getColumns(null, schema, sqlInfo.tableName, null)) {
			while (rs.next()) {
				String colName = rs.getString("COLUMN_NAME");
				int dataType = rs.getInt("DATA_TYPE");
				int size = rs.getInt("COLUMN_SIZE");
				if (dataType == Types.VARCHAR || dataType == Types.CHAR) {
					map.put(colName, size);
				}
			}
		}
		return map;
	}
	
	/* ================= 核心：根据参数推断哪个字段超长 ================= */
	
	private void checkParametersAndThrow(BoundSql boundSql,
										 SqlInfo sqlInfo,
										 Map<String, Integer> varcharLimitMap) {
		
		List<String> columns = sqlInfo.columns;
		List<ParameterMapping> params = boundSql.getParameterMappings();
		Object parameterObject = boundSql.getParameterObject();
		
		if (parameterObject == null || params == null || params.isEmpty()) return;
		
		MetaObject metaObject = SystemMetaObject.forObject(parameterObject);
		int count = Math.min(columns.size(), params.size());
		
		for (int i = 0; i < count; i++) {
			String column = columns.get(i);
			Integer maxLen = varcharLimitMap.get(column);
			if (maxLen == null || maxLen <= 0) continue;
			
			ParameterMapping pm = params.get(i);
			String propName = pm.getProperty();
			
			Object val = null;
			if (metaObject.hasGetter(propName)) {
				val = metaObject.getValue(propName);
			} else if (parameterObject instanceof Map) {
				val = ((Map<?, ?>) parameterObject).get(propName);
			}
			
			if (val instanceof String) {
				String str = (String) val;
				if (str.length() > maxLen) {
					// 到这里就是你想要的：报错之后再算出字段 + 长度
					String error = String.format("Column [%s] value too long.actual:[%s] actual length = %d, max = %d", column, str, str.length(), maxLen);
					throw new RuntimeException(error);
				}
			}
		}
	}
}
