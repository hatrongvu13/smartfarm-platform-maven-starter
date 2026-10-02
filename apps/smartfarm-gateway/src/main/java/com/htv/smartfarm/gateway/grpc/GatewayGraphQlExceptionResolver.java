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
        if (exception instanceof GatewayGraphQlException value) {
            return GraphqlErrorBuilder.newError(environment)
                    .message(value.getMessage())
                    .extensions(Map.of("code", value.classification()))
                    .build();
        }
        if (exception instanceof IllegalArgumentException value) {
            return GraphqlErrorBuilder.newError(environment)
                    .message(value.getMessage() == null ? "Invalid request" : value.getMessage())
                    .extensions(Map.of("code", "BAD_REQUEST"))
                    .build();
        }
        return null;
    }
}
