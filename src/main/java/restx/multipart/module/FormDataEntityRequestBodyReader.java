package restx.multipart.module;

import com.google.common.io.Files;
import org.eclipse.jetty.util.MultiPartInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import restx.RestxContext;
import restx.RestxRequest;
import restx.WebException;
import restx.common.TypeReference;
import restx.entity.EntityRequestBodyReader;
import restx.http.HttpStatus;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.Part;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;

public class FormDataEntityRequestBodyReader implements EntityRequestBodyReader<Collection<Part>> {

    private static final Logger logger = LoggerFactory.getLogger(FormDataEntityRequestBodyReader.class);

    @Override
    public Type getType() {
        return MultipartFormDataContentTypeModule.TYPE;
    }

    //TODO Declare Multipart annotation to wrap @Consumes("multipart/form-data") and @MultipartConfig
    // to create MultipartConfigElement
    @Override
    public Collection<Part> readBody(RestxRequest req, RestxContext ctx) throws IOException {
        File tempDir = Files.createTempDir();
        MultiPartInputStream multiPartInputStream =
                new MultiPartInputStream(req.getContentStream(), req.getContentType(),
                                         new MultipartConfigElement(tempDir.getAbsolutePath()),
                                         tempDir);
        try {
            return multiPartInputStream.getParts();
        } catch (ServletException e) {
            logger.error("Can't parse request", e);
            throw new WebException(HttpStatus.BAD_REQUEST, String.format("Can't parse request\n%s", e));
        }
    }
}
