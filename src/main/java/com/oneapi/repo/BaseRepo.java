package com.oneapi.repo;

import java.sql.Connection;
import java.sql.SQLException;
import com.oneapi.config.DatabaseConfig;

import javax.sql.DataSource;

/**
 * 仓库层基类。
 * <p>
 * 职责：统一从 HikariCP 连接池获取 {@link Connection}，供各具体仓库的 CRUD 操作使用。
 * 所有仓库子类必须遵守 try-with-resources 约定（见各子类方法实现），以避免连接泄漏。
 * <p>
 * 错误处理约定：子类的 SQLException 一律 log 后返回空集合或 null，
 * 不向上抛出——这是为了上游 controller 能用 200/200 空集 表达"暂时无数据"，
 * 而非用 500 表达"数据库炸了"（数据库错误会通过 /api/status 暴露）。
 */
public abstract class BaseRepo {
    private final DataSource dataSource;

    protected BaseRepo() {
        this.dataSource = DatabaseConfig.getDataSource();
    }

    /** For testing — inject custom DataSource. */
    protected BaseRepo(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    protected Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
