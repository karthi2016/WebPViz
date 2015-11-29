package db;

import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.util.JSON;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import models.*;
import models.xml.PVizPoint;
import models.xml.Plotviz;
import models.xml.XMLLoader;
import org.apache.commons.io.FilenameUtils;
import org.bson.Document;
import play.Logger;
import scala.collection.immutable.Stream;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class MongoDB {
    private MongoCollection<Document> filesCollection;
    private MongoCollection<Document> clustersCollection;
    private MongoCollection<Document> groupsCollection;

    private static MongoDB db = new MongoDB();

    public static MongoDB getInstance() {
        return db;
    }

    private static SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private MongoDB() {
        Config conf = ConfigFactory.load();
        String mongoHost = conf.getString(Constants.DB.MONGO_HOST);
        int mongoPort = conf.getInt(Constants.DB.MONGO_PORT);

        MongoClient mongoClient;
        if (mongoHost != null) {
            Logger.info("Using mongo DB " + mongoHost + ":" + mongoPort);
            mongoClient = new MongoClient(mongoHost, mongoPort);
        } else {
            Logger.info("Using local mongo DB " + "localhost:27017");
            mongoClient = new MongoClient("localhost", 27017);
        }

        MongoDatabase db = mongoClient.getDatabase(Constants.DB.DB_NAME);

        filesCollection = db.getCollection(Constants.DB.FILES_COLLECTION);
        clustersCollection = db.getCollection(Constants.DB.CLUSTERS_COLLECTION);
        groupsCollection = db.getCollection(Constants.DB.GROUPS_COLLECTION);
    }

    /**
     * Insert a single fie pviz file or a txt file
     * @param pvizName name of the uploaded file
     * @param description description of the file
     * @param uploader the uploader name
     * @param file the actual file
     * @throws Exception  if the file cannot be inserted
     */
    public void insertSingleFile(String pvizName, String description, int uploader, File file) throws Exception {
        String dateString = format.format(new Date());
        int timeSeriesId = Math.abs(new Random().nextInt());
        Document mainDoc = new Document();
        mainDoc.append(Constants.ID_FIELD, timeSeriesId);
        mainDoc.append("_id", timeSeriesId);
        mainDoc.append(Constants.NAME_FIELD, pvizName);
        mainDoc.append(Constants.DESC_FIELD, description);
        mainDoc.append(Constants.UPLOADED_FIELD, uploader);
        mainDoc.append(Constants.DATE_CREATION_FIELD, dateString);
        mainDoc.append(Constants.STATUS_FIELD, "active");

        List<Document> resultSets = new ArrayList<Document>();

        String resultSetName = "timeseries_" + pvizName + "_" + 0;
        insertXMLFile(0, resultSetName, description, uploader, new FileInputStream(file), timeSeriesId, 0L, pvizName);
        Document resultSet = createResultSet(0, resultSetName, description, dateString, uploader, timeSeriesId, 0, pvizName);
        resultSets.add(resultSet);

        mainDoc.append(Constants.RESULTSETS_FIELD, resultSets);
        filesCollection.insertOne(mainDoc);
    }

    /**
     * Delete the time series files
     * @param timeSeriesId delete the file
     * @return true if delete successful
     */
    public boolean deleteTimeSeries(int timeSeriesId) {
        filesCollection.deleteOne(new Document(Constants.ID_FIELD, timeSeriesId));
        clustersCollection.deleteMany(new Document(Constants.TIME_SERIES_ID_FIELD, timeSeriesId));
        return true;
    }

    public boolean groupExists(Group group) {
        Document groupDocument = new Document();
        groupDocument.append(Constants.Group.NAME, group.name);
        FindIterable<Document> iterable = groupsCollection.find(groupDocument);
        return iterable.iterator().hasNext();
    }

    public void insertGroup(Group group) {
        Document groupDocument = new Document();
        groupDocument.append(Constants.Group.NAME, group.name);
        groupDocument.append(Constants.Group.DESCRIPTION, group.description);
        groupsCollection.insertOne(groupDocument);
    }

    public void updateGroup(Group oldGroup, Group newGroup) {
        Document oldGroupDocument = new Document();
        oldGroupDocument.append(Constants.Group.NAME, oldGroup.name);

        Document groupDocument = new Document();
        groupDocument.append(Constants.Group.NAME, newGroup.name);
        groupDocument.append(Constants.Group.DESCRIPTION, newGroup.description);

        groupsCollection.findOneAndReplace(oldGroupDocument, groupDocument);
    }

    public void deleteGroup(Group group) {
        Document groupDocument = new Document();
        groupDocument.append(Constants.Group.NAME, group.name);
        groupsCollection.deleteOne(groupDocument);
    }

    /**
     * Insert a zip file containing the time series files
     * @param pvizName name of the uploaded plotviz file
     * @param description description
     * @param uploader the uploader id
     * @param fileName file name
     * @throws Exception if an error happens while inserting
     */
    public void insertZipFile(String pvizName, String description, int uploader, File fileName) throws Exception {
        String dateString = format.format(new Date());
        int timeSeriesId = Math.abs(new Random().nextInt());
        Document mainDoc = new Document();
        mainDoc.append(Constants.ID_FIELD, timeSeriesId);
        mainDoc.append("_id", timeSeriesId);
        mainDoc.append(Constants.NAME_FIELD, pvizName);
        mainDoc.append(Constants.DESC_FIELD, description);
        mainDoc.append(Constants.UPLOADED_FIELD, uploader);
        mainDoc.append(Constants.DATE_CREATION_FIELD, dateString);
        mainDoc.append(Constants.STATUS_FIELD, Constants.STATUS_PENDING);
        List<Document> emptyResultSets = new ArrayList<Document>();
        mainDoc.append(Constants.RESULTSETS_FIELD, emptyResultSets);
        filesCollection.insertOne(mainDoc);

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ZipFile zipFile = new ZipFile(fileName);
                    Enumeration<?> enu = zipFile.entries();
                    List<String> filesInOrder = new ArrayList<String>();
                    Map<String, ZipEntry> fileMap = new HashMap<String, ZipEntry>();
                    while (enu.hasMoreElements()) {
                        ZipEntry zipEntry = (ZipEntry) enu.nextElement();
                        String name = zipEntry.getName();
                        String ext = FilenameUtils.getExtension(name);
                        String realFileName = FilenameUtils.getName(name);

                        File file = new File(name);
                        if (name.endsWith("/")) {
                            file.mkdirs();
                            continue;
                        }

                        File parent = file.getParentFile();
                        if (parent != null) {
                            parent.mkdirs();
                        }

                        if (ext != null && ext.equals("index")) {
                            BufferedReader bufRead = new BufferedReader(new InputStreamReader(zipFile.getInputStream(zipEntry)));
                            String inputLine;
                            while ((inputLine = bufRead.readLine()) != null) {
                                filesInOrder.add(inputLine);
                            }
                            continue;
                        }
                        fileMap.put(realFileName, zipEntry);
                    }

                    int i = 0;
                    List<Document> resultSets = new ArrayList<Document>();
                    for (String f : filesInOrder) {
                        if (fileMap.get(f) != null) {
                            String resultSetName = "timeseries_" + f + "_" + i;
                            insertXMLFile(i, resultSetName, description, uploader, zipFile.getInputStream(fileMap.get(f)), timeSeriesId, (long) i, f);
                            Document resultSet = createResultSet(i, resultSetName, description, dateString, uploader, timeSeriesId, i, f);
                            resultSets.add(resultSet);
                            i++;

                        }
                    }
                    mainDoc.append(Constants.RESULTSETS_FIELD, resultSets);
                    zipFile.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                mainDoc.append(Constants.STATUS_FIELD, "active");

                filesCollection.replaceOne(new Document(Constants.ID_FIELD, timeSeriesId), mainDoc);
            }
        });
        t.start();
    }

    public Document createResultSet(int id, String name, String description, String dateCreation, int uploaderId, int timeSeriesId, int timeSeriesSeqNumber, String originalFileName) {
        Document document = new Document();
        document.append(Constants.ID_FIELD, id).append(Constants.NAME_FIELD, name).append(Constants.DESCRIPTION_FIELD, description).
                append(Constants.DATE_CREATION_FIELD, dateCreation).append(Constants.UPLOADER_ID_FIELD, uploaderId).
                append(Constants.TIME_SERIES_ID_FIELD, timeSeriesId).append(Constants.TIME_SERIES_SEQ_NUMBER_FIELD, timeSeriesSeqNumber).
                append(Constants.FILE_NAME_FIELD, originalFileName);
        return document;
    }

    public void insertXMLFile(int id, String name, String description, int uploader, InputStream file,
                              int parent, Long sequenceNumber, String originalFileName) throws Exception {
        Document clustersDbObject = new Document();
        clustersDbObject.append(Constants.ID_FIELD, id);
        clustersDbObject.append(Constants.NAME_FIELD, name);
        clustersDbObject.append(Constants.DESC_FIELD, description);
        clustersDbObject.append(Constants.UPLOADED_FIELD, uploader);
        clustersDbObject.append(Constants.FILE_NAME_FIELD,  originalFileName);
        clustersDbObject.append(Constants.TIME_SERIES_ID_FIELD, parent);
        clustersDbObject.append(Constants.TIME_SERIES_SEQ_NUMBER_FIELD, sequenceNumber);

        Plotviz plotviz = XMLLoader.load(file);
        List<models.xml.Cluster> clusters = plotviz.getClusters();
        Map<Integer, Document> clusterDBObjects = new HashMap<Integer, Document>();
        for (models.xml.Cluster cl : clusters) {
            Document c = new Document();
            c.put(Constants.CLUSTERID_FIELD, cl.getKey());
            c.put(Constants.COLOR_FIELD, new Document().append("a", cl.getColor().getA()).append("b", cl.getColor().getB()).append("g", cl.getColor().getG()).append("r", cl.getColor().getR()));
            c.put(Constants.LABEL_FIELD, cl.getLabel());
            c.put(Constants.SIZE_FIELD, cl.getSize());
            c.put(Constants.VISIBLE_FIELD, cl.getVisible());
            c.put(Constants.SHAPE_FIELD, cl.getShape());
            clusterDBObjects.put(cl.getKey(), c);
        }

        List<PVizPoint> points = plotviz.getPoints();
        Map<Integer, List<Document>> pointsForClusters = new HashMap<Integer, List<Document>>();
        for (int i = 0; i < points.size(); i++) {
            PVizPoint point = points.get(i);
            int clusterkey = point.getClusterkey();

            List<Document> basicDBObjectList = pointsForClusters.get(clusterkey);
            if (basicDBObjectList == null) {
                basicDBObjectList = new ArrayList<Document>();
                pointsForClusters.put(clusterkey, basicDBObjectList);
            }
            Document pointDBObject = createPoint(point.getLocation().getX(), point.getLocation().getY(), point.getLocation().getZ(), clusterkey);
            basicDBObjectList.add(pointDBObject);
        }

        Iterator<Map.Entry<Integer, List<Document>>> entries = pointsForClusters.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Integer, List<Document>> e = entries.next();
            if (e.getValue() != null && e.getValue().size() > 0) {
                Document clusterDBObject = clusterDBObjects.get(e.getKey());
                clusterDBObject.append("points", e.getValue());
            } else {
                Logger.info("Remove: " + e.getKey());
                entries.remove();
            }
        }

        for(Iterator<Map.Entry<Integer, Document>> it = clusterDBObjects.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Document> entry = it.next();
            if (!pointsForClusters.containsKey(entry.getKey())) {
                it.remove();
            }
        }

        // add each cluster to clusters object
        List<Document> clustersList = new ArrayList<Document>(clusterDBObjects.values());
        clustersDbObject.append("clusters", clustersList);

        clustersCollection.insertOne(clustersDbObject);
    }

    public Document createPoint( Float x, Float y, Float z, int cluster){
        Document object = new Document();
        object.append("x", x);
        object.append("y", y);
        object.append("z", z);
        object.append("cluster", cluster);

        return object;
    }

    public String queryTimeSeries(int id) {
        Document query = new Document(Constants.ID_FIELD, id);
        FindIterable<Document> iterable = filesCollection.find(query);
        for (Document d : iterable) {
            return JSON.serialize(d);
        }
        return null;
    }

    public String queryFile(int tid, int fid) {
        Document query = new Document(Constants.ID_FIELD, fid).append(Constants.TIME_SERIES_ID_FIELD, tid);
        FindIterable<Document> iterable = clustersCollection.find(query);
        for (Document d : iterable) {
            return JSON.serialize(d);
        }
        return null;
    }

    public List<Cluster> clusters(int tid, int fid) {
        Document query = new Document(Constants.ID_FIELD, fid).append(Constants.TIME_SERIES_ID_FIELD, tid);
        FindIterable<Document> iterable = clustersCollection.find(query);
        List<Cluster> clusters = new ArrayList<Cluster>();
        for (Document d : iterable) {
            Object clusterObjects = d.get("clusters");
            if (clusterObjects instanceof List) {
                for (Object c : (List)clusterObjects) {
                    Document clusterDocument = (Document) c;
                    Cluster cluster = new Cluster();
                    cluster.resultSet = fid;
                    cluster.id = (Integer) clusterDocument.get(Constants.ID_FIELD);
                    cluster.cluster = (Integer) clusterDocument.get(Constants.CLUSTERID_FIELD);
                    cluster.shape = (String) clusterDocument.get(Constants.SHAPE_FIELD);
                    cluster.visible = (int) clusterDocument.get(Constants.VISIBLE_FIELD);
                    cluster.size = (int) clusterDocument.get(Constants.SIZE_FIELD);
                    cluster.label = (String) clusterDocument.get(Constants.LABEL_FIELD);
                    cluster.color = color((Document) clusterDocument.get(Constants.COLOR_FIELD));
                    Logger.info(JSON.serialize(clusterDocument));
                    clusters.add(cluster);
                }
            }
        }
        return clusters;
    }

    private Color color(Document document) {
        Color color = new Color();
        color.a = (int) document.get("a");
        color.b = (int) document.get("b");
        color.g = (int) document.get("g");
        color.r = (int) document.get("r");
        return color;
    }

    public ResultSet individualFile(int timeSeriesId) {
        Document query = new Document(Constants.ID_FIELD, timeSeriesId);

        FindIterable<Document> iterable = filesCollection.find(query);
        for (Document document : iterable) {
            Object resultSetsObject = document.get(Constants.RESULTSETS_FIELD);
            if (resultSetsObject instanceof List) {
                for (Object documentObject : (List)resultSetsObject) {
                    Document resultDocument = (Document) documentObject;
                    int fId = (Integer) resultDocument.get(Constants.ID_FIELD);
                    ResultSet resultSet = new ResultSet();
                    try {
                        resultSet.dateCreation = format.parse((String) resultDocument.get(Constants.DATE_CREATION_FIELD));
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                    resultSet.id = (Integer) resultDocument.get(Constants.ID_FIELD);
                    resultSet.name = (String) resultDocument.get(Constants.NAME_FIELD);
                    resultSet.description = (String) resultDocument.get(Constants.DESCRIPTION_FIELD);
                    resultSet.uploaderId = (Integer) resultDocument.get(Constants.UPLOADER_ID_FIELD);
                    resultSet.fileName = (String) resultDocument.get(Constants.FILE_NAME_FIELD);
                    resultSet.timeSeriesSeqNumber = (Integer) resultDocument.get(Constants.TIME_SERIES_SEQ_NUMBER_FIELD);
                    resultSet.timeSeriesId = (Integer) resultDocument.get(Constants.TIME_SERIES_ID_FIELD);
                    return resultSet;
                }
            }
        }
        return null;
    }

    public ResultSet individualFile(int timeSeriesId, int fileId) {
        Document query = new Document(Constants.ID_FIELD, timeSeriesId);

        FindIterable<Document> iterable = filesCollection.find(query);
        for (Document document : iterable) {
            Object resultSetsObject = document.get(Constants.RESULTSETS_FIELD);
            if (resultSetsObject instanceof List) {
                for (Object documentObject : (List)resultSetsObject) {
                    Document resultDocument = (Document) documentObject;
                    int fId = (Integer) resultDocument.get(Constants.ID_FIELD);
                    if (fId == fileId) {
                        ResultSet resultSet = new ResultSet();
                        try {
                            resultSet.dateCreation = format.parse((String) resultDocument.get(Constants.DATE_CREATION_FIELD));
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
                        resultSet.id = (Integer) resultDocument.get(Constants.ID_FIELD);
                        resultSet.name = (String) resultDocument.get(Constants.NAME_FIELD);
                        resultSet.description = (String) resultDocument.get(Constants.DESCRIPTION_FIELD);
                        resultSet.uploaderId = (Integer) resultDocument.get(Constants.UPLOADER_ID_FIELD);
                        resultSet.fileName = (String) resultDocument.get(Constants.FILE_NAME_FIELD);
                        resultSet.timeSeriesSeqNumber = (Integer) resultDocument.get(Constants.TIME_SERIES_SEQ_NUMBER_FIELD);
                        resultSet.timeSeriesId = (Integer) resultDocument.get(Constants.TIME_SERIES_ID_FIELD);
                        return resultSet;
                    }
                }
            }
        }
        return null;
    }

    public List<ResultSet> individualFiles() {
        FindIterable<Document> iterable = filesCollection.find();
        List<ResultSet> resultSetList = new ArrayList<ResultSet>();
        for (Document document : iterable) {
            Object resultSetsObject = document.get(Constants.RESULTSETS_FIELD);
            if (resultSetsObject instanceof List) {
                for (Object documentObject : (List)resultSetsObject) {
                    Document resultDocument = (Document) documentObject;
                    ResultSet resultSet = new ResultSet();
                    try {
                        resultSet.dateCreation = format.parse((String) resultDocument.get(Constants.DATE_CREATION_FIELD));
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                    resultSet.id = (Integer) resultDocument.get(Constants.ID_FIELD);
                    resultSet.name = (String) resultDocument.get(Constants.NAME_FIELD);
                    resultSet.description = (String) resultDocument.get(Constants.DESCRIPTION_FIELD);
                    resultSet.uploaderId = (Integer) resultDocument.get(Constants.UPLOADER_ID_FIELD);
                    resultSet.fileName = (String) resultDocument.get(Constants.FILE_NAME_FIELD);
                    resultSet.timeSeriesSeqNumber = (Integer) resultDocument.get(Constants.TIME_SERIES_SEQ_NUMBER_FIELD);
                    resultSet.timeSeriesId = (Integer) resultDocument.get(Constants.TIME_SERIES_ID_FIELD);
                    resultSetList.add(resultSet);
                }
            }
        }
        return resultSetList;
    }

    /**
     * Get the uploaded entity list
     *
     * @return uploaded entity list
     */
    public List<TimeSeries> timeSeriesList() {
        FindIterable<Document> iterable = filesCollection.find();
        List<TimeSeries> timeSeriesList = new ArrayList<TimeSeries>();
        for (Document document : iterable) {
            TimeSeries timeSeries = new TimeSeries();
            try {
                timeSeries.dateCreation = format.parse((String) document.get(Constants.DATE_CREATION_FIELD));
            } catch (ParseException e) {
                e.printStackTrace();
            }
            timeSeries.id = (Integer) document.get(Constants.ID_FIELD);
            timeSeries.name = (String) document.get(Constants.NAME_FIELD);
            timeSeries.description = (String) document.get(Constants.DESC_FIELD);
            timeSeries.uploaderId = (Integer) document.get(Constants.UPLOADED_FIELD);
            timeSeries.status = (String) document.get(Constants.STATUS_FIELD);
            Object resultSetsObject = document.get(Constants.RESULTSETS_FIELD);
            if (resultSetsObject != null && resultSetsObject instanceof List) {
                if (((List)resultSetsObject).size() > 1) {
                    timeSeries.typeString = "T";
                } else {
                    timeSeries.typeString = "S";
                }
            } else {
                timeSeries.typeString = "S";
            }
            timeSeriesList.add(timeSeries);
        }
        return timeSeriesList;
    }

    public TimeSeries timeSeries(int id) {
        FindIterable<Document> iterable = filesCollection.find(new Document(Constants.ID_FIELD, id));
        for (Document d : iterable) {
            TimeSeries timeSeries = new TimeSeries();
            timeSeries.id = (Integer) d.get(Constants.ID_FIELD);
            timeSeries.name = (String) d.get(Constants.NAME_FIELD);
            return timeSeries;
        }
        return null;
    }
}
