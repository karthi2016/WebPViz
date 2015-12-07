package db;

import com.mongodb.BasicDBList;
import com.mongodb.client.FindIterable;
import com.mongodb.util.JSON;
import models.*;
import models.Cluster;
import models.Color;
import models.xml.*;
import org.apache.commons.io.FilenameUtils;
import org.bson.Document;
import play.Logger;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ArtifactDAO {
    private static ArtifactDAO db = new ArtifactDAO();

    public static ArtifactDAO getInstance() {
        return db;
    }

    private static SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * Insert a single fie pviz file or a txt file
     * @param pvizName name of the uploaded file
     * @param description description of the file
     * @param uploader the uploader name
     * @param file the actual file
     * @throws Exception  if the file cannot be inserted
     */
    public void insertSingleFile(String pvizName, String description, int uploader, File file, String group) throws Exception {
        MongoConnection con = MongoConnection.getInstance();
        String dateString = format.format(new Date());
        int timeSeriesId = Math.abs(new Random().nextInt());
        Document mainDoc = new Document();
        mainDoc.append(Constants.Artifact.ID_FIELD, timeSeriesId);
        mainDoc.append(Constants.Artifact.NAME_FIELD, pvizName);
        mainDoc.append(Constants.Artifact.DESC_FIELD, description);
        mainDoc.append(Constants.Artifact.USER, uploader);
        mainDoc.append(Constants.Artifact.DATE_CREATION_FIELD, dateString);
        mainDoc.append(Constants.Artifact.STATUS_FIELD, "active");
        mainDoc.append(Constants.Artifact.GROUP_FIELD, group);
        mainDoc.append(Constants.Artifact.VERSION, 1);
        mainDoc.append(Constants.Artifact.TYPE, Constants.ArtifactType.PLOTVIZ);

        String resultSetName = pvizName + "/";
        // insert the file content to the files collection
        insertXMLFile(0, resultSetName, description, uploader, new FileInputStream(file), timeSeriesId, 0L, pvizName);
        con.filesCollection.insertOne(mainDoc);
    }

    /**
     * Delete the time series files
     * @param timeSeriesId delete the file
     * @return true if delete successful
     */
    public boolean deleteTimeSeries(int timeSeriesId) {
        MongoConnection con = MongoConnection.getInstance();
        con.filesCollection.deleteOne(new Document(Constants.Artifact.ID_FIELD, timeSeriesId));
        con.clustersCollection.deleteMany(new Document(Constants.File.TIME_SERIES_ID_FIELD, timeSeriesId));
        return true;
    }



    /**
     * Insert a zip file containing the time series files
     * @param pvizName name of the uploaded plotviz file
     * @param description description
     * @param uploader the uploader id
     * @param fileName file name
     * @throws Exception if an error happens while inserting
     */
    public void insertZipFile(String pvizName, String description, int uploader, File fileName, String group) throws Exception {
        MongoConnection con = MongoConnection.getInstance();
        String dateString = format.format(new Date());
        int timeSeriesId = Math.abs(new Random().nextInt());
        Document mainDoc = new Document();
        mainDoc.append(Constants.Artifact.ID_FIELD, timeSeriesId);
        mainDoc.append("_id", timeSeriesId);
        mainDoc.append(Constants.Artifact.NAME_FIELD, pvizName);
        mainDoc.append(Constants.Artifact.DESC_FIELD, description);
        mainDoc.append(Constants.Artifact.USER, uploader);
        mainDoc.append(Constants.Artifact.DATE_CREATION_FIELD, dateString);
        mainDoc.append(Constants.Artifact.STATUS_FIELD, Constants.ArtifactStatus.PENDING);
        List<Document> emptyResultSets = new ArrayList<Document>();
        mainDoc.append(Constants.RESULTSETS_FIELD, emptyResultSets);
        mainDoc.append(Constants.Artifact.GROUP_FIELD, group);
        mainDoc.append(Constants.Artifact.TYPE, Constants.ArtifactType.TIME_SERIES);
        con.filesCollection.insertOne(mainDoc);

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
                mainDoc.append(Constants.Artifact.STATUS_FIELD, Constants.ArtifactStatus.ACTIVE);

                con.filesCollection.replaceOne(new Document(Constants.Artifact.ID_FIELD, timeSeriesId), mainDoc);
            }
        });
        t.start();
    }

    public Document createResultSet(int id, String name, String description, String dateCreation, int uploaderId, int timeSeriesId, int timeSeriesSeqNumber, String originalFileName) {
        Document document = new Document();
        document.append(Constants.Artifact.ID_FIELD, id).append(Constants.Artifact.NAME_FIELD, name).append(Constants.Artifact.DESCRIPTION_FIELD, description).
                append(Constants.Artifact.DATE_CREATION_FIELD, dateCreation).append(Constants.UPLOADER_ID_FIELD, uploaderId).
                append(Constants.File.TIME_SERIES_ID_FIELD, timeSeriesId).append(Constants.File.TIME_SERIES_SEQ_NUMBER_FIELD, timeSeriesSeqNumber).
                append(Constants.File.FILE_NAME_FIELD, originalFileName);
        return document;
    }

    private BasicDBList color(int a, int r, int g, int b) {
        BasicDBList list = new BasicDBList();
        list.add(a);
        list.add(r);
        list.add(g);
        list.add(b);
        return list;
    }

    public void insertXMLFile(int id, String name, String description, int uploader, InputStream file,
                              int parent, Long sequenceNumber, String originalFileName) throws Exception {
        MongoConnection con = MongoConnection.getInstance();
        // maximum number of points per file
        final int maxPointsPerFile = 250000;
        Map<Integer, Integer> clusterPointCount = new HashMap<>();
        Document rootObject = createRootFileObject(id, name, description, uploader, parent, sequenceNumber, originalFileName);

        Plotviz plotviz = XMLLoader.load(file);

        // traverse through the clusters and create the cluster list
        List<models.xml.Cluster> clusters = plotviz.getClusters();
        Map<Integer, Document> clusterDBObjectList = new HashMap<Integer, Document>();
        for (models.xml.Cluster cl : clusters) {
            Document c = new Document();
            c.put(Constants.Cluster.KEY, cl.getKey());
            c.put(Constants.Cluster.COLOR, color(cl.getColor().getA(), cl.getColor().getB(), cl.getColor().getG(), cl.getColor().getR()));
            c.put(Constants.Cluster.LABEL, cl.getLabel());
            c.put(Constants.Cluster.SIZE, cl.getSize());
            c.put(Constants.Cluster.VISIBILE, cl.getVisible());
            c.put(Constants.Cluster.SHAPE, cl.getShape());
            clusterDBObjectList.put(cl.getKey(), c);
        }

        // now traverse through the points and create the point list
        List<PVizPoint> points = plotviz.getPoints();
        // point key for each cluster
        Map<Integer, List<Integer>> pointsForClusters = new HashMap<Integer, List<Integer>>();
        List<Document> pointList = new ArrayList<Document>();
        for (PVizPoint point : points) {
            int clusterkey = point.getClusterkey();
            int pointKey = point.getKey();
            List<Integer> clusterPoints = pointsForClusters.get(clusterkey);
            if (clusterPoints == null) {
                clusterPoints = new ArrayList<Integer>();
                pointsForClusters.put(clusterkey, clusterPoints);
            }
            Document pointDBObject = createPoint(point.getLocation().getX(), point.getLocation().getY(), point.getLocation().getZ(), clusterkey, pointKey);
            // add the key to cluster and point to point list
            clusterPoints.add(pointKey);
            pointList.add(pointDBObject);
        }

        Iterator<Map.Entry<Integer, List<Integer>>> entries = pointsForClusters.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Integer, List<Integer>> e = entries.next();
            if (e.getValue() != null && e.getValue().size() > 0) {
                Document clusterDBObject = clusterDBObjectList.get(e.getKey());
                clusterDBObject.append(Constants.Cluster.POINTS, e.getValue());
                clusterPointCount.put(e.getKey(), e.getValue().size());
            } else {
                Logger.info("Remove: " + e.getKey());
                entries.remove();
            }
        }

        // remove the clusters without any points
        for(Iterator<Map.Entry<Integer, Document>> it = clusterDBObjectList.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Document> entry = it.next();
            if (!pointsForClusters.containsKey(entry.getKey())) {
                it.remove();
            }
        }

        // add the point key list to clusters
        for (Map.Entry<Integer, List<Integer>> e : pointsForClusters.entrySet()) {
            Document clusterDocument = clusterDBObjectList.get(e.getKey());
            clusterDocument.append(Constants.Cluster.POINTS, e.getValue());
        }

        // clusters to root object
        rootObject.append(Constants.File.CLUSTERS, clusterDBObjectList.values());
        // add points to root object list
        rootObject.append(Constants.File.POINTS, pointList);

        // now insert the edges if there are any
        List<Document> edgesList = new ArrayList<Document>();
        List<Edge> edges = plotviz.getEdges();
        if (edges != null && edges.size() > 0) {
            for (Edge e : edges) {
                Document edgeDoc = new Document();
                edgeDoc.append(Constants.Edge.ID, e.getKey());
                List<Vertex> vertexes = e.getVertices();
                List<String> vertices = new ArrayList<>();
                if (vertexes != null && vertexes.size() > 0) {
                    List<Document> vertexDocs = new ArrayList<Document>();
                    for (Vertex v : vertexes) {
                        vertices.add(v.getKey());
                    }
                    edgeDoc.append(Constants.Edge.VERTICES, vertices);
                } else {
                    // no point adding this edge, because it doesn't have vertices
                    continue;
                }
                edgesList.add(edgeDoc);
            }
            rootObject.append(Constants.File.EDGES, edgesList);
        }
        con.clustersCollection.insertOne(rootObject);
    }

    /**
     * Constructs the root cluster object
     * @param id id
     * @param name name
     * @param description description
     * @param uploader user
     * @param parent the big file
     * @param sequenceNumber sequence
     * @param originalFileName original file
     * @return document
     */
    private Document createRootFileObject(int id, String name, String description, int uploader, int parent, Long sequenceNumber, String originalFileName) {
        Document rootObject = new Document();
        rootObject.append(Constants.Artifact.ID_FIELD, id);
        rootObject.append(Constants.Artifact.NAME_FIELD, name);
        rootObject.append(Constants.Artifact.DESC_FIELD, description);
        rootObject.append(Constants.Artifact.USER, uploader);
        rootObject.append(Constants.File.FILE_NAME_FIELD, originalFileName);
        rootObject.append(Constants.File.TIME_SERIES_ID_FIELD, parent);
        rootObject.append(Constants.File.TIME_SERIES_SEQ_NUMBER_FIELD, sequenceNumber);
        return rootObject;
    }

    public Document createPoint(Float x, Float y, Float z, int cluster, int pointKey) {
        Document object = new Document();
        object.append(Constants.Point.KEY, pointKey);
        object.append(Constants.Point.CLUSTER, cluster);

        List<Float> list = new ArrayList<>();
        list.add(x);
        list.add(y);
        list.add(z);
        object.append(Constants.Point.VALUE, list);
        return object;
    }

    public String queryTimeSeries(int id) {
        MongoConnection con = MongoConnection.getInstance();
        Document query = new Document(Constants.Artifact.ID_FIELD, id);
        FindIterable<Document> iterable = con.filesCollection.find(query);
        for (Document d : iterable) {
            return JSON.serialize(d);
        }
        return null;
    }

    public String queryFile(int tid, int fid) {
        MongoConnection con = MongoConnection.getInstance();
        Document query = new Document(Constants.Artifact.ID_FIELD, fid).append(Constants.File.TIME_SERIES_ID_FIELD, tid);
        FindIterable<Document> iterable = con.clustersCollection.find(query);
        Document mainDoc = new Document();
        for (Document d : iterable) {
            mainDoc = d;
            break;
        }

        List<Document> clusters = new ArrayList<>();
        List<Document> edges = new ArrayList<>();
        for (Document d : iterable) {
            Object clusterObjects = d.get("clusters");
            if (clusterObjects instanceof List) {
                for (Object c : (List)clusterObjects) {
                    Document clusterDocument = (Document) c;
                    clusters.add(clusterDocument);
                }
            }

            Object edgeObjects = d.get("edges");
            if (edgeObjects instanceof List) {
                for (Object c : (List)edgeObjects) {
                    Document edgeDocument = (Document) c;
                    edges.add(edgeDocument);
                }
            }
        }
        mainDoc.append("clusters", clusters);
        mainDoc.append("edges", edges);
        return JSON.serialize(mainDoc);
    }

    public List<Cluster> clusters(int tid, int fid) {
        MongoConnection con = MongoConnection.getInstance();
        Document query = new Document(Constants.Artifact.ID_FIELD, fid).append(Constants.File.TIME_SERIES_ID_FIELD, tid);
        FindIterable<Document> iterable = con.clustersCollection.find(query);
        List<Cluster> clusters = new ArrayList<Cluster>();
        for (Document d : iterable) {
            Object clusterObjects = d.get("clusters");
            if (clusterObjects instanceof List) {
                for (Object c : (List)clusterObjects) {
                    Document clusterDocument = (Document) c;
                    Cluster cluster = new Cluster();
                    cluster.resultSet = fid;
                    cluster.id = (Integer) clusterDocument.get(Constants.CLUSTERID_FIELD);
                    cluster.cluster = (Integer) clusterDocument.get(Constants.CLUSTERID_FIELD);
                    cluster.shape = (String) clusterDocument.get(Constants.SHAPE_FIELD);
                    cluster.visible = (int) clusterDocument.get(Constants.VISIBLE_FIELD);
                    cluster.size = (int) clusterDocument.get(Constants.SIZE_FIELD);
                    cluster.label = (String) clusterDocument.get(Constants.LABEL_FIELD);
                    cluster.color = color((Document) clusterDocument.get(Constants.COLOR_FIELD));
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
        MongoConnection con = MongoConnection.getInstance();
        Document query = new Document(Constants.Artifact.ID_FIELD, timeSeriesId);

        FindIterable<Document> iterable = con.filesCollection.find(query);
        for (Document document : iterable) {
            Object resultSetsObject = document.get(Constants.RESULTSETS_FIELD);
            if (resultSetsObject instanceof List) {
                for (Object documentObject : (List)resultSetsObject) {
                    Document resultDocument = (Document) documentObject;
                    int fId = (Integer) resultDocument.get(Constants.Artifact.ID_FIELD);
                    ResultSet resultSet = new ResultSet();
                    try {
                        resultSet.dateCreation = format.parse((String) resultDocument.get(Constants.Artifact.DATE_CREATION_FIELD));
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                    resultSet.id = (Integer) resultDocument.get(Constants.Artifact.ID_FIELD);
                    resultSet.name = (String) resultDocument.get(Constants.Artifact.NAME_FIELD);
                    resultSet.description = (String) resultDocument.get(Constants.Artifact.DESCRIPTION_FIELD);
                    resultSet.uploaderId = (Integer) resultDocument.get(Constants.UPLOADER_ID_FIELD);
                    resultSet.fileName = (String) resultDocument.get(Constants.File.FILE_NAME_FIELD);
                    resultSet.timeSeriesSeqNumber = (Integer) resultDocument.get(Constants.File.TIME_SERIES_SEQ_NUMBER_FIELD);
                    resultSet.timeSeriesId = (Integer) resultDocument.get(Constants.File.TIME_SERIES_ID_FIELD);
                    return resultSet;
                }
            }
        }
        return null;
    }

    public ResultSet individualFile(int timeSeriesId, int fileId) {
        MongoConnection con = MongoConnection.getInstance();
        Document query = new Document(Constants.Artifact.ID_FIELD, timeSeriesId);

        FindIterable<Document> iterable = con.filesCollection.find(query);
        for (Document document : iterable) {
            Object resultSetsObject = document.get(Constants.RESULTSETS_FIELD);
            if (resultSetsObject instanceof List) {
                for (Object documentObject : (List)resultSetsObject) {
                    Document resultDocument = (Document) documentObject;
                    int fId = (Integer) resultDocument.get(Constants.Artifact.ID_FIELD);
                    if (fId == fileId) {
                        ResultSet resultSet = new ResultSet();
                        try {
                            resultSet.dateCreation = format.parse((String) resultDocument.get(Constants.Artifact.DATE_CREATION_FIELD));
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
                        resultSet.id = (Integer) resultDocument.get(Constants.Artifact.ID_FIELD);
                        resultSet.name = (String) resultDocument.get(Constants.Artifact.NAME_FIELD);
                        resultSet.description = (String) resultDocument.get(Constants.Artifact.DESCRIPTION_FIELD);
                        resultSet.uploaderId = (Integer) resultDocument.get(Constants.UPLOADER_ID_FIELD);
                        resultSet.fileName = (String) resultDocument.get(Constants.File.FILE_NAME_FIELD);
                        resultSet.timeSeriesSeqNumber = (Integer) resultDocument.get(Constants.File.TIME_SERIES_SEQ_NUMBER_FIELD);
                        resultSet.timeSeriesId = (Integer) resultDocument.get(Constants.File.TIME_SERIES_ID_FIELD);
                        return resultSet;
                    }
                }
            }
        }
        return null;
    }

    public List<ResultSet> individualFiles() {
        MongoConnection con = MongoConnection.getInstance();
        FindIterable<Document> iterable = con.filesCollection.find();
        List<ResultSet> resultSetList = new ArrayList<ResultSet>();
        for (Document document : iterable) {
            Object resultSetsObject = document.get(Constants.RESULTSETS_FIELD);
            if (resultSetsObject instanceof List) {
                for (Object documentObject : (List)resultSetsObject) {
                    Document resultDocument = (Document) documentObject;
                    ResultSet resultSet = new ResultSet();
                    try {
                        resultSet.dateCreation = format.parse((String) resultDocument.get(Constants.Artifact.DATE_CREATION_FIELD));
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                    resultSet.id = (Integer) resultDocument.get(Constants.Artifact.ID_FIELD);
                    resultSet.name = (String) resultDocument.get(Constants.Artifact.NAME_FIELD);
                    resultSet.description = (String) resultDocument.get(Constants.Artifact.DESCRIPTION_FIELD);
                    resultSet.uploaderId = (Integer) resultDocument.get(Constants.UPLOADER_ID_FIELD);
                    resultSet.fileName = (String) resultDocument.get(Constants.File.FILE_NAME_FIELD);
                    resultSet.timeSeriesSeqNumber = (Integer) resultDocument.get(Constants.File.TIME_SERIES_SEQ_NUMBER_FIELD);
                    resultSet.timeSeriesId = (Integer) resultDocument.get(Constants.File.TIME_SERIES_ID_FIELD);
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
        MongoConnection con = MongoConnection.getInstance();
        FindIterable<Document> iterable = con.filesCollection.find();
        return getTimeSeriesList(iterable);
    }

    public List<TimeSeries> timeSeriesList(Group group) {
        MongoConnection con = MongoConnection.getInstance();
        Document findDoc = new Document();
        findDoc.append(Constants.Artifact.GROUP_FIELD, group.name);

        FindIterable<Document> iterable = con.filesCollection.find(findDoc);
        return getTimeSeriesList(iterable);
    }

    private List<TimeSeries> getTimeSeriesList(FindIterable<Document> iterable) {
        List<TimeSeries> timeSeriesList = new ArrayList<TimeSeries>();
        for (Document document : iterable) {
            TimeSeries timeSeries = new TimeSeries();
            try {
                timeSeries.dateCreation = format.parse((String) document.get(Constants.Artifact.DATE_CREATION_FIELD));
            } catch (ParseException e) {
                e.printStackTrace();
            }
            timeSeries.id = (Integer) document.get(Constants.Artifact.ID_FIELD);
            timeSeries.name = (String) document.get(Constants.Artifact.NAME_FIELD);
            timeSeries.description = (String) document.get(Constants.Artifact.DESC_FIELD);
            timeSeries.uploaderId = (Integer) document.get(Constants.Artifact.USER);
            timeSeries.status = (String) document.get(Constants.Artifact.STATUS_FIELD);
            timeSeries.group = (String) document.get(Constants.Artifact.GROUP_FIELD);
            if (timeSeries.group == null || "".equals(timeSeries.group)) {
                timeSeries.group = Constants.Group.DEFAULT_GROUP;
            }
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

    public boolean timeSeriesExists(TimeSeries timeSeries) {
        MongoConnection con = MongoConnection.getInstance();
        Document doc = new Document();
        doc.append(Constants.Artifact.ID_FIELD, timeSeries.id);
        FindIterable<Document> iterable = con.filesCollection.find(doc);
        return iterable.iterator().hasNext();
    }

    public void updateTimeSeries(TimeSeries old, TimeSeries newTimeSeries) {
        MongoConnection con = MongoConnection.getInstance();
        Logger.info("updating the document with id " + old.id + " with group: " + newTimeSeries.group + " desc: " + newTimeSeries.description);
        Document oldGroupDocument = new Document();
        oldGroupDocument.append(Constants.Artifact.ID_FIELD, old.id);

        FindIterable<Document> iterable = con.filesCollection.find(oldGroupDocument);
        Document findDocument = null;
        for (Document d : iterable) {
            findDocument = d;
            break;
        }

        if (findDocument != null) {
            findDocument.append(Constants.Artifact.GROUP_FIELD, newTimeSeries.group);
            findDocument.append(Constants.Artifact.DESC_FIELD, newTimeSeries.description);
            con.filesCollection.replaceOne(oldGroupDocument, findDocument);
        }
    }

    public TimeSeries timeSeries(int id) {
        MongoConnection con = MongoConnection.getInstance();
        FindIterable<Document> iterable = con.filesCollection.find(new Document(Constants.Artifact.ID_FIELD, id));
        for (Document d : iterable) {
            TimeSeries timeSeries = new TimeSeries();
            timeSeries.id = (Integer) d.get(Constants.Artifact.ID_FIELD);
            timeSeries.name = (String) d.get(Constants.Artifact.NAME_FIELD);
            return timeSeries;
        }
        return null;
    }
}