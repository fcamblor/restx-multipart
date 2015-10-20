package restx.multipart;

import org.apache.commons.fileupload.MultipartStream;

import java.io.*;

/**
 * @author fcamblor
 */
public class PartListeners {

    public interface Stream {
        void onFilePart(MultipartStream multipartStream, String filename, String contentType) throws IOException;
    }

    public interface Text {
            void onTextPart(String content) throws IOException;
        }

    public static class PipedStream implements Stream, Closeable {
        private InputStream pipedInputStream;
        private String filename;
        private String contentType;

        @Override
        public void onFilePart(MultipartStream multipartStream, String filename, String contentType) throws IOException {
            this.pipedInputStream = pipeOutputStreamToInputStream(multipartStream);
            this.filename = filename;
            this.contentType = contentType;
        }

        private static InputStream pipeOutputStreamToInputStream(MultipartStream multipartStream) throws IOException {
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();) {
                multipartStream.readBodyData(outputStream);
                InputStream is = new ByteArrayInputStream(outputStream.toByteArray());
                return is;
            }
        }

        public boolean hasStream() {
            return pipedInputStream != null;
        }

        public InputStream getPipedInputStream() {
            return pipedInputStream;
        }

        public String getFilename() {
            return filename;
        }

        public String getContentType() {
            return contentType;
        }

        @Override
        public void close() throws IOException {
            if (hasStream()) {
                this.pipedInputStream.close();
            }
        }
    }

}
