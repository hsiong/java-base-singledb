/**
 * Copyright 2016 SmartBear Software
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tech.ynfy.frame.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SqlUtilProperty {

    /**
     * 表别名
     */
    String tableAlias() default "";
    
    /**
     * 数据库字段名（如果与Java字段名不一致时使用）
     */
    String column() default "";
    
    /**
     * 查询类型
     */
    QueryType type() default QueryType.EQ;
    
    /**
     * 是否忽略该字段
     */
    boolean ignore() default false;
    
    /**
     * 查询类型枚举
     */
    enum QueryType {
        /**
         * 等于
         */
        EQ,
        /**
         * 不等于
         */
        NE,
        /**
         * 大于
         */
        GT,
        /**
         * 大于等于
         */
        GE,
        /**
         * 小于
         */
        LT,
        /**
         * 小于等于
         */
        LE,
        /**
         * 模糊查询
         */
        LIKE,
        /**
         * 左模糊查询
         */
        LIKE_LEFT,
        /**
         * 右模糊查询
         */
        LIKE_RIGHT,
        /**
         * IN 查询
         */
        IN,
        /**
         * NOT IN 查询
         */
        NOT_IN,
        /**
         * BETWEEN
         */
        BETWEEN,
        /**
         * IS NULL
         */
        IS_NULL,
        /**
         * IS NOT NULL
         */
        IS_NOT_NULL
    }

}
