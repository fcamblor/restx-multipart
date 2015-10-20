package restx.multipart;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.net.HttpHeaders;
import org.apache.commons.fileupload.MultipartStream;
import restx.RestxRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;


public class PartsReader {
    private final RestxRequest req;
    private Map<String, PartListeners.Stream> streamPartListeners = new HashMap<>();
    private Map<String, PartListeners.Text> textPartListeners = new HashMap<>();

    public PartsReader(RestxRequest req) {
        this.req = req;
    }

    public PartsReader onFilePart(String name, PartListeners.Stream listener) {
        streamPartListeners.put(name, listener);
        return this;
    }

    public PartsReader onTextPart(String name, PartListeners.Text listener) {
        textPartListeners.put(name, listener);
        return this;
    }

    /**
     * Read request parts and notifies the given listener of each part.
     *
     * A listener mechanism is used because data is read from a stream, so you can't get all parts without actually
     * reading them.
     */
    public void readParts() throws IOException {
        if(streamPartListeners.isEmpty() && textPartListeners.isEmpty()) {
            throw new IllegalStateException("You called readParts() without registering part listeners (onXXXPart())");
        }

        String contentType = req.getContentType();
        int boundaryIndex = contentType.indexOf("boundary=");
        byte[] boundary = (contentType.substring(boundaryIndex + 9)).getBytes();

        MultipartStream multipartStream =  new MultipartStream(req.getContentStream(), boundary);
        boolean nextPart = multipartStream.skipPreamble();
        while (nextPart) {
            Map<String, String> headers = parseHeaders(multipartStream.readHeaders());
            String contentDisposition = headers.get("content-disposition");
            if (contentDisposition == null) {
                throw new IOException("unsupported multi part format: no content-disposition on one of the parts");
            }

            Map<String, String> parameters = new LinkedHashMap<>();
            if (contentDisposition.startsWith("form-data;")) {
                Iterable<String> split = Splitter.on(';').trimResults().split(contentDisposition.substring("form-data;".length()));
                for (String s : split) {
                    int i = s.indexOf('=');
                    String name = s.substring(0, i);

                    // Note: value is surrounded by " that we get rid of
                    String value = s.substring(i + 2, s.length() - 1);
                    parameters.put(name, value);
                }
            } else {
                throw new IOException("unsupported multi part format: a content-disposition does not start with form-data");
            }

            String partName = parameters.get("name");
            if (partName == null) {
                throw new IOException("unsupported multi part format: a part has no 'name'");
            }

            String cType = headers.get("content-type");
            if (cType != null) {
                if(!streamPartListeners.containsKey(partName)){
                    throw new IllegalStateException(String.format("file part listener declaration missing for part with name [%s]", partName));
                }
                streamPartListeners.get(partName).onFilePart(multipartStream, parameters.get("filename"), cType);
            } else {
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                multipartStream.readBodyData(data);
                /**
                 * The component used to manage upload client side (angular-file-upload) send TextPart and FilePart when used with
                 * IE not supporting html5. It causes issue, because the file is unreadable after upload.
                 * So this (ugly) hack allows to manage upload from old-IE : it ignore TextPart.
                 */
                Optional<String> userAgent = req.getHeader(HttpHeaders.USER_AGENT);
                if (!userAgent.isPresent() || (!userAgent.get().equals("Shockwave Flash") && !userAgent.get().contains("windows"))) {
                    // we use UTF-8 charset, but we should better get it from the request...
                    if(!textPartListeners.containsKey(partName)){
                        throw new IllegalStateException(String.format("text part listener declaration missing for part with name [%s]", partName));
                    }
                    textPartListeners.get(partName).onTextPart(data.toString(Charsets.UTF_8.name()));
                }
            }

            nextPart = multipartStream.readBoundary();
        }
    }

    private Map<String, String> parseHeaders(String headerPart) {
        final int len = headerPart.length();
        Map<String, String> headers = new LinkedHashMap<>();
        int start = 0;
        for (;;) {
            int end = parseEndOfLine(headerPart, start);
            if (start == end) {
                break;
            }
            StringBuilder header = new StringBuilder(headerPart.substring(start, end));
            start = end + 2;
            while (start < len) {
                int nonWs = start;
                while (nonWs < len) {
                    char c = headerPart.charAt(nonWs);
                    if (c != ' '  &&  c != '\t') {
                        break;
                    }
                    ++nonWs;
                }
                if (nonWs == start) {
                    break;
                }
                // Continuation line found
                end = parseEndOfLine(headerPart, nonWs);
                header.append(" ").append(headerPart.substring(nonWs, end));
                start = end + 2;
            }
            parseHeaderLine(headers, header.toString());
        }
        return headers;
    }

    /**
     * Skips bytes until the end of the current line.
     * @param headerPart The headers, which are being parsed.
     * @param end Index of the last byte, which has yet been
     *   processed.
     * @return Index of the \r\n sequence, which indicates
     *   end of line.
     */
    private int parseEndOfLine(String headerPart, int end) {
        int index = end;
        for (;;) {
            int offset = headerPart.indexOf('\r', index);
            if (offset == -1  ||  offset + 1 >= headerPart.length()) {
                throw new IllegalStateException(
                    "Expected headers to be terminated by an empty line.");
            }
            if (headerPart.charAt(offset + 1) == '\n') {
                return offset;
            }
            index = offset + 1;
        }
    }

    /**
     * Reads the next header line.
     * @param headers String with all headers.
     * @param header Map where to store the current header.
     */
    private void parseHeaderLine(Map<String, String> headers, String header) {
        final int colonOffset = header.indexOf(':');
        if (colonOffset == -1) {
            // This header line is malformed, skip it.
            return;
        }
        String headerName = header.substring(0, colonOffset).trim();
        String headerValue =
            header.substring(header.indexOf(':') + 1).trim();
        headers.put(headerName.toLowerCase(Locale.ENGLISH), headerValue);
    }

}