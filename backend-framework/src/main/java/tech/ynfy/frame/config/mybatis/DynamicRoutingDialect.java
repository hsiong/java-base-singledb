package tech.ynfy.frame.config.mybatis;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.extension.plugins.pagination.DialectModel;
import com.baomidou.mybatisplus.extension.plugins.pagination.dialects.IDialect;
import com.baomidou.mybatisplus.extension.plugins.pagination.dialects.PostgreDialect;

public class DynamicRoutingDialect implements IDialect {
	
	private final IDialect postgresDialect = new PostgreDialect();
	
	@Override
	public DialectModel buildPaginationSql(String originalSql, long offset, long limit) {
//		String ds = DynamicDataSourceContextHolder.peek(); // 当前数据源 @DS
		
		return postgresDialect.buildPaginationSql(originalSql, offset, limit);
		
	}
}


