package com.htv.smartfarm.gateway.grpc;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import java.util.Map;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.stereotype.Component;
import graphql.schema.DataFetchingEnvironment;

@Component
public class GatewayGraphQlExceptionResolver extends DataFetcherExceptionResolverAdapter {
    @Override
    protected GraphQLError resolveToSingleError(Throwable exception, DataFetchingEnvironment environment) {
        if (!(exception instanceof GatewayGraphQlException value)) return null;
        return GraphqlErrorBuilder.newError(environment)
                .message(value.getMessage())
                .extensions(Map.of("code", value.classification()))
                .build();
    }
}
