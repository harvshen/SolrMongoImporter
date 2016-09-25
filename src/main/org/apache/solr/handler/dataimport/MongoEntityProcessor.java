package org.apache.solr.handler.dataimport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.solr.handler.dataimport.DataImportHandlerException.SEVERE;

/**
 * Credit to James75 and marciogoda<br>
 * <li>https://github.com/james75</li>
 * <li>marciogoda: https://github.com/marciogoda/SolrMongoImporter</li>
 * <br>
 * My contribution: fix the date/time format issue, thus make delta import and delta deletion work with last_index_time variable
 *
 */
public class MongoEntityProcessor extends EntityProcessorBase {
    private static final Logger LOG = LoggerFactory.getLogger(EntityProcessorBase.class);
    //pattern example: 2016-03-20 22:14:56
    private final static Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}");

    protected MongoDataSource dataSource;

    private String collection;

    @Override
    public void init(Context context) {
        super.init(context);
        this.collection = context.getEntityAttribute( COLLECTION );
        if( this.collection == null ) {
            throw new DataImportHandlerException(SEVERE,
                    "Collection must be supplied");

        }
        this.dataSource  = (MongoDataSource) context.getDataSource();
    }

    protected void initQuery(String q) {
        try {
            DataImporter.QUERY_COUNT.get().incrementAndGet();
            rowIterator = dataSource.getData( q, this.collection );
            this.query = q;
        } catch (DataImportHandlerException e) {
            throw e;
        } catch (Exception e) {
            LOG.error( "The query failed '" + q + "'", e);
            throw new DataImportHandlerException(DataImportHandlerException.SEVERE, e);
        }
    }

    @Override
    public Map<String, Object> nextRow() {
        if (rowIterator == null) {
            String query = this.getQuery();
            initQuery(context.replaceTokens(query));

        }
        return getNext();
    }

    @Override
    public Map<String, Object> nextModifiedRowKey() {
        if (rowIterator == null) {
            String deltaQuery = context.getEntityAttribute(DELTA_QUERY);
            if(deltaQuery == null) return null;

            String replaceDeltaQuery = context.replaceTokens(deltaQuery);
            initQuery(fixDateRegEx(replaceDeltaQuery));
        }
        return getNext();
    }

    @Override
    public Map<String, Object> nextDeletedRowKey() {
        if (rowIterator == null) {
            String deletedPkQuery = context.getEntityAttribute(DEL_PK_QUERY);
            if(deletedPkQuery == null) return null;

            String delQuery = context.replaceTokens(deletedPkQuery);
            initQuery(fixDateRegEx(delQuery));
        }
        return getNext();
    }

    @Override
    public Map<String, Object> nextModifiedParentRowKey() {
        if(this.rowIterator == null) {
            String parentDeltaQuery = this.context.getEntityAttribute("parentDeltaQuery");
            if(parentDeltaQuery == null) {
                return null;
            }

            LOG.info("Running parentDeltaQuery for Entity: " + this.context.getEntityAttribute("name"));
            this.initQuery(this.context.replaceTokens(parentDeltaQuery));
        }

        return this.getNext();
    }

    public String getQuery() {
        String queryString = this.context.getEntityAttribute(QUERY);
        if("FULL_DUMP".equals(this.context.currentProcess())) {
            return queryString;
        } else if("DELTA_DUMP".equals(this.context.currentProcess())) {
            return this.context.getEntityAttribute(DELTA_IMPORT_QUERY);

        } else {
            return null;
        }
    }

/* simple test method
    public static void main(String[] args) throws IOException {
        //String rawMessage = "{$and : [ {$or : [{'status' : 'DELETED'},{'status' : 'UNLISTED'} ]}, {'modifiedAt':{$gt:{$date:'2016-03-20 22:14:56'}} } ] }";
        String rawMessage = "{$and : [ {$or : [{'status' : 'AVAILABLE'},{'status' : 'SOLD'} ]}, {'modifiedAt':{$gt:{$date:'2016-03-20 22:14:56'}} } ] }";
        String result1 = fixDate(rawMessage);
        String result2 = fixDateRegEx(rawMessage);

        System.out.println(result1);
        System.out.println(result2);

        System.out.println(result1.equals(result2));
    }
*/

    /**
     * <p>
     * <b>The problem</b>
     * Example query defined in dih-config.xml looks like this
     * {$and : [ {$or : [{'status' : 'DELETED'},{'status' : 'UNLISTED'} ]}, {'modifiedAt':{$gt:{$date:'${dih.last_index_time}'}} } ] }
     *
     * When delta import job runs, the ${dih.last_index_time} will be replaced with a real date time value (which is stored in SOLR_HOME/server/solr/INDEXNAME/conf/dataimport.properties)
     * {$and : [ {$or : [{'status' : 'DELETED'},{'status' : 'UNLISTED'} ]}, {'modifiedAt':{$gt:{$date:'2016-03-20 22:14:56'}} } ] }
     *
     * However, this date time isn't ISO standard so MongoDB doesn't understand it.
     *</p>
     * <p>
     * <b>The fix</b>
     * Fix date string from
     * 2016-03-20 22:14:56
     * TO
     * 2016-03-20T22:14:56Z
     *
     * After the fix, the query string will look like
     * * {$and : [ {$or : [{'status' : 'DELETED'},{'status' : 'UNLISTED'} ]}, {'modifiedAt':{$gt:{$date:'2016-03-20T22:14:56Z'}} } ] }
     * </p>
     *
     * @TODO: ideally find a better way to replace the correct ${dih.last_index_time} with ISO format
     *
     * @param query the query defined in dih-config.xml
     * @return query with correct ISO date/time populated
     */
    private static String fixDateRegEx(String query){
        Matcher matcher = DATE_PATTERN.matcher(query);
        StringBuffer output = new StringBuffer();
        //use regular expression in case there are multiple ${dih.last_index_time} defined in dih-config.xml
        while(matcher.find()){
            String date2BeReplaced = matcher.group(0);

            //get rid of matched portion
            matcher.appendReplacement(output, "");

            //replace whitespace with T and append Z to the end
            output.append(date2BeReplaced.replace(" ", "T")).append("Z");
        }

        //append the rest
        matcher.appendTail(output);
        return output.toString();
    }


    private static String fixDate(String query) {
        int lastQuotationMark = query.lastIndexOf("'");
        if(lastQuotationMark>-1){
            int secondQuotationMark = query.lastIndexOf("'", lastQuotationMark - 1);
            if(secondQuotationMark > -1){
                StringBuffer output = new StringBuffer();
                String dateString = query.substring(secondQuotationMark+1,lastQuotationMark);
                output.append(query.substring(0,secondQuotationMark+1));
                output.append(dateString.replace(" ", "T")).append("Z");
                output.append(query.substring(lastQuotationMark));
                return output.toString();
            }
        }
        return query;
    }


    public static final String QUERY      = "query";

    public static final String DELTA_QUERY = "deltaQuery";

    public static final String DELTA_IMPORT_QUERY = "deltaImportQuery";

    public static final String DEL_PK_QUERY = "deletedPkQuery";

    public static final String COLLECTION = "collection";
}
