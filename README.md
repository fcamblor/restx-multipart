restx-multipart
=========
Simple restx-module to handle mime "multipart/form-data".
Currently only *consumption* (for file upload) is handled, no multipart/form-data producer has been implemented yet

Usage
=========

Simply add the dependency to your project, then use it like this :

```java
package restx.multipart;

import com.mongodb.BasicDBObject;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSInputFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import restx.Status;
import restx.annotations.Consumes;
import restx.annotations.POST;
import restx.annotations.RestxResource;
import restx.factory.Component;

import java.io.IOException;

@RestxResource @Component
public class UploadResource {
    
    private static final Logger LOG = LoggerFactory.getLogger(UploadResource.class);
    private final GridFS gridFS;
    
    public UploadResource(GridFS gridFS){
        this.gridFS = gridFS;
    }

    @POST("/upload")
    @Consumes("multipart/form-data")
    // To make it work, you need to inject a PartsReader onto your endpoint. 
    // It will brings you a fluent API allowing to react to multipart form data inside your request
    public Status upload(PartsReader partsReader) throws IOException {
        try(
            // Creating a piped stream grabber to retrieve a particular multipart form data representing a binary file
            PartListeners.PipedStreamGrabber restxStreamListener = new PartListeners.PipedStreamGrabber(true);
            // Creating simple text grabber to retrieve a particular multipart form data text content
            PartListeners.TextContentGrabber assetTypeGrabber = new PartListeners.TextContentGrabber(true);
            // Same than previous one, except that if the multipart form data is absent, readParts() won't complain
            PartListeners.TextContentGrabber metaDataGrabber = new PartListeners.TextContentGrabber(false);
        ){

            partsReader
                    .onTextPart("assetType", assetTypeGrabber)
                    .onTextPart("metadata", metaDataGrabber)
                    .onFilePart("file", restxStreamListener)
                    .readParts();

            System.out.println(String.format("Asset type received : %s", assetTypeGrabber.getContent())); 
            System.out.println(String.format("Metadata received : %s", metaDataGrabber.getContent()));
            
            if(restxStreamListener.hasStream()) {
                // Storing uploaded file into gridFS...
                GridFSInputFile file = gridFS.createFile(restxStreamListener.getPipedInputStream(), true);
                // .. and keeping its content type
                file.setContentType(restxStreamListener.getContentType());

                // Storing some metadata alongside the object in gridfs
                file.setMetaData(new BasicDBObject());
                file.getMetaData().put("assetType", assetTypeGrabber.getContent());
                if (metaDataGrabber.getContent() != null) {
                    file.getMetaData().put("metadata", metaDataGrabber.getContent());
                }

                file.save();
            }
        }
        
        return Status.of("created");
    }
}
```

Bonus : Downloading file sample
=========
You can write corresponding `AssetResource` allowing to download previously uploaded file stored into mongodb with following route :
```
import com.google.common.collect.ImmutableMap;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSDBFile;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import restx.*;
import restx.factory.Component;

import javax.inject.Named;
import java.io.IOException;

@Component
public class AssetResource extends ResourcesRoute {

    static Logger log = LoggerFactory.getLogger(AssetResource.class);

    private final GridFS gridFS;

    public AssetResource(
            @Named("app.assetsName") String name,
            @Named("app.assetsBaseRestPath") String baseRestPath,
            GridFS gridFS
    ){
        super(name, baseRestPath, "", ImmutableMap.<String,String>of("", "index.html"));
        this.gridFS = gridFS;
    }

    @Override
    public void handle(RestxRequestMatch match, RestxRequest req, RestxResponse resp, RestxContext ctx) throws IOException {
        GridFSDBFile file = gridFS.findOne(new ObjectId(req.getRestxUri().replace(getBaseRestPath(), "")));
        if(file == null){
            return;
        }
        resp.setContentType(file.getContentType());
        file.writeTo(resp.getOutputStream());
    }
}
```
