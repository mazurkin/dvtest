package org.test;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Utility {

    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT
            .withIgnoreEmptyLines()
            .withTrim()
            .withDelimiter(',')
            .withAllowMissingColumnNames(false)
            .withIgnoreEmptyLines(true)
            .withIgnoreHeaderCase(true)
            .withFirstRecordAsHeader()
            .withQuote('"');

    private static final URL INPUT_RESOURCE = Utility.class.getResource("/hits.csv");

    public static List<String> loadUrls(String server) throws Exception {
        List<String> result = new ArrayList<>();

        try (InputStream is = INPUT_RESOURCE.openStream();
             Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
             CSVParser csvParser = new CSVParser(reader, CSV_FORMAT))
        {
            for (CSVRecord csvRecord : csvParser) {
                String inventoryType = csvRecord.get("inventorytype");

                String url = csvRecord.get("resolvedpageurl");
                String bundle = csvRecord.get("appbundle");
                String ip = csvRecord.get("visitoripaddress");
                String agent = csvRecord.get("useragentstring");

                if (StringUtils.isEmpty(inventoryType)) {
                    continue;
                }

                if (StringUtils.isAllEmpty(url, bundle)) {
                    continue;
                }

                URIBuilder b = new URIBuilder("http://" + server + "/dv-iqc");
                b.addParameter("partnerid", "3775");

                switch (inventoryType) {
                    case "1":
                        b.addParameter("url", url);
                        break;
                    case "2":
                        b.addParameter("bundleid", bundle);
                        break;
                    default:
                        continue;
                }

                b.addParameter("useragent", agent);
                b.addParameter("ip", ip);

                result.add(b.toString());
            }
        }

        return result;
    }
}
