package com.cerner.jwala.service.impl;

import com.cerner.jwala.service.DbServiceException;
import com.cerner.jwala.service.H2ServerType;
import org.h2.tools.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

/**
 * H2 TCP Server implementation of {@link AbstractH2ServerServiceImpl}
 *
 * Created by JC043760 on 8/25/2016
 */
public class H2TcpServerServiceImpl extends AbstractH2ServerServiceImpl {

    private final static Logger LOGGER = LoggerFactory.getLogger(H2TcpServerServiceImpl.class);

    private static final String DEFAULT_TCP_SERVER_PARAMS = "-tcpPort,9094,-tcpAllowOthers";

    public H2TcpServerServiceImpl(final String tcpServerParams) {
        super(tcpServerParams == null ? DEFAULT_TCP_SERVER_PARAMS : tcpServerParams, H2ServerType.TCP);
        if (tcpServerParams == null) {
            LOGGER.warn("tcpServerParams is null, loading default tcpServerParams values \"{}\"", DEFAULT_TCP_SERVER_PARAMS);
        }
    }

    @Override
    protected Server createServer(final String [] serverParams) throws DbServiceException {
        try {
            return Server.createTcpServer(serverParams);
        } catch (final SQLException e) {
            LOGGER.error("Failed to create H2 TCP Server!", e);
            throw new DbServiceException(e);
        }
    }
}