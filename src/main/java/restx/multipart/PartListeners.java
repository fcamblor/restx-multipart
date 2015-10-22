package restx.multipart;

import com.google.common.base.Optional;
import org.apache.commons.fileupload.MultipartStream;

import java.io.*;

/**
 * @author fcamblor
 */
public class PartListeners {

    public interface PartListener {
        boolean isMandatory();
    }

    public interface Stream extends PartListener {
        void onFilePart(MultipartStream multipartStream, Optional<String> filename, String contentType) throws IOException;
    }

    public interface Text extends PartListener {
        void onTextPart(String content) throws IOException;
    }

    public static abstract class AbstractPartListener implements PartListener {
        protected boolean mandatory;

        public AbstractPartListener(boolean mandatory) {
            this.mandatory = mandatory;
        }

        public boolean isMandatory() {
            return mandatory;
        }
    }

    public static class PipedStreamGrabber extends AbstractPartListener implements Stream, AutoCloseable {
        private InputStream pipedInputStream;
        private String filename;
        private String contentType;

        // By default, grabber is mandatory
        public PipedStreamGrabber(){ this(true); }
        public PipedStreamGrabber(boolean mandatory) {
            super(mandatory);
        }

        @Override
        public void onFilePart(MultipartStream multipartStream, Optional<String> filename, String contentType) throws IOException {
            this.pipedInputStream = pipeOutputStreamToInputStream(multipartStream);
            this.filename = filename.orNull();
            this.contentType = contentType;
        }

        private static InputStream pipeOutputStreamToInputStream(MultipartStream multipartStream) throws IOException {
            // Important note: we're loading the WHOLE file content into memory here (through ByteArrayOutputStream)
            // This is not ideal if we're working with big amounts of uploaded data
            // We may thing of using Piped Input/Output stream instead, however, when passing PipedOutputStream
            // to multipartStream.readBodyData(), thread is hanging forever (it seems like if output stream is buffered,
            // piped stream are freezing the thread .. which is not happening with ByteArrayOutputStream as this is not
            // a buffered stream)
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

    public static class TextContentGrabber extends AbstractPartListener implements Text {
        private String content;

        // By default, grabber is mandatory
        public TextContentGrabber(){ this(true); }
        public TextContentGrabber(boolean mandatory) {
            super(mandatory);
        }

        @Override
        public void onTextPart(String content) throws IOException {
            this.content = content;
        }

        public String getContent() {
            return content;
        }
    }

}
