package restx.multipart.module;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import restx.RestxContext;
import restx.RestxRequest;
import restx.entity.EntityRequestBodyReader;
import restx.multipart.PartsReader;

import java.io.IOException;
import java.lang.reflect.Type;

public class FormDataEntityRequestBodyReader implements EntityRequestBodyReader<PartsReader> {

    private static final Logger LOG = LoggerFactory.getLogger(FormDataEntityRequestBodyReader.class);

    @Override
    public Type getType() {
        return MultipartFormDataContentTypeModule.TYPE;
    }

    //TODO Declare Multipart annotation to wrap @Consumes("multipart/form-data") and @MultipartConfig
    // to create MultipartConfigElement
    @Override
    public PartsReader readBody(RestxRequest req, RestxContext ctx) throws IOException {
        return new PartsReader(req);
    }
}
