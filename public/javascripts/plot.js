/* varibles*/
//TODO need to add reply option to make the slider go back to the start and reload data from the start
//TODO try to remove all other collections possible and work with sections object

//Three js global varibles
var camera, scene, renderer, sprite, colors = [], particles = [], controls, light, currentParticles = [];
var container, stats;
var heus = [0.05, 0.3, 0.15, 0.2, 0.25, 0.3, 0.35, 0.4, 0.45, 0.5, 0.55, 0.6, 0.65, 0.7, 0.75, 0.8, 0.85, 0.9, 0.95, 0.05, 0.3, 0.15, 0.2, 0.25, 0.3, 0.35, 0.4, 0.45, 0.5, 0.55, 0.6, 0.65, 0.7, 0.75, 0.8, 0.85, 0.9, 0.95];
var scene3d;
var colors = [];
var colorlist = {};
var sections = [];


//Single Plot Varibles
var clusterUrl;
var clusters;
var resultSetId;
var resultData;

//Time Series Vars
var particleSets = {};
var fileNames = {};
var timeSeriesData = [];
var isPlaying = false;
var isPaused = false;
var resultSets;
var removedclusters = [];
var recoloredclusters = [];
var currentLoadedStart, currentLoadedEnd, timeSeriesLength;

//Constants
var TIME_BETWEEN_PLOTS_IN_MILLS = 300;
var MAX_PLOTS_STORED = 20;
var LOAD_SIZE = 5;

//Generate the check box list for clusters
function generateCheckList(list, initcolors) {

    var tabletop = "<table class='table table-striped' id='cluster_table' style='background-color: #EAEAEA; padding-top: 0px'>"
        + "<thead>"
        + "<tr class='headings'>"
        + "<th>"
        + "<input type='checkbox' id='check-all' disabled='true' class='flat'> Cluster"
        + "</th>"
        + "<th class='column-title'>Label</th>"
        + "<th class='column-title'>Size</th>"
        + "</tr>"
        + "</thead>"
        + "<tbody>"

    var tablerows = "";

    for (var key in list) {
        if (list.hasOwnProperty(key)) {
            tablerows += "<tr class='even pointer'>"
                + "<td class='a-center'>"
                + "<input type='checkbox' class='flat' name='table_records' checked value='" + key + "'>"
                + "<label class='color-box-label'>" + key + "</label> "
                + "<div class='input-group color-pic1' style='width: 15px;height: 15px; display: inline-flex; padding-left: 20px;padding-top: 2px'>"
                + "<input value='" + initcolors[key] + "' class='form-control' type='hidden' id='" + key + "'>"
                + "<span class='input-group-addon'><i style='background-color: rgb(1, 343, 69);'></i></span>"
                + "</div>"
                + "</td>"
                + "<td class=' '>" +  list[key].label + "</span></td>"
                + "<td class=' '>" + list[key].length + "</td>"
                + "</tr>"
        }
    }

    var tableend = "</tbody>"
        + "</table>"


    return tabletop + tablerows + tableend;
}

//Plot functions
function visualize(resultSetUrl, resultSet, id) {
    clusterUrl = resultSetUrl;
    clusters = resultSet.clusters;
    resultSetId = id;
    generateGraph();
    animate();
}

function visualizeTimeSeries(resultSetUrl, timeSeries, id) {
    resultSets = timeSeries.resultsets;
    timeSeriesLength = resultSets.length;
    generateTimeSeries(resultSets);
}


function generateGraph() {

    setupThreeJs();

    var cluster;
    var geometry = {};
    var hsl;
    var sections = {};
    $.getJSON(clusterUrl, function (data) {
        fileName = data.fileName;
        $( "#amount" ).val(fileName)
        for (var i = 0; i < clusters.length; i++) {
            var clusterdata = data.clusters[i];
            var clusterid = clusterdata.clusterid;

            var clustercolor = clusterdata.color
            if(clustercolor == null)
                clustercolor = {"a":randomRBG(),"b":randomRBG(),"g":randomRBG(),"r":randomRBG()}

            if (!sections.hasOwnProperty(clusterdata.clusterid))
                sections[clusterdata.clusterid] = {"length":clusterdata.points.length, "size":clusterdata.size,
                    "shape":clusterdata.shape, "visible":clusterdata.visible, "color": clustercolor, "label":clusterdata.label}

            if(!geometry.hasOwnProperty(clusterdata.clusterid)){
                geometry[clusterdata.clusterid] = new THREE.Geometry();
                colors[clusterdata.clusterid] = new Array();
                currentParticles[clusterdata.clusterid] = new Array();
            }
            colors[clusterdata.clusterid].push(new THREE.Color("rgb(" + clustercolor.r + "," + clustercolor.g + "," + clustercolor.b + ")"));

            if(clusterdata)
            for (var k in clusterdata.points) {
                var p = clusterdata.points[k];

                var vertex = new THREE.Vector3(p.x , p.y , p.z);

                geometry[clusterdata.clusterid].vertices.push(vertex);

                if(k==0)
                    continue
                colors[clusterdata.clusterid].push(new THREE.Color("rgb(" + clustercolor.r + "," + clustercolor.g + "," + clustercolor.b + ")"));
            }
        }
        for (var key in geometry) {
            if (geometry.hasOwnProperty(key)) {
                colorlist[key] = colors[key][0].getHexString();
                geometry[key].colors = colors[key];
                currentParticles[key] = new THREE.PointCloud(geometry[key], loadMatrial(sections[key].size,sections[key].shape, false));
                scene3d.add(currentParticles[key]);

            }
        }
        window.document.getElementById("cluster_table_div").innerHTML = generateCheckList(sections, colorlist);
       // stats.domElement.style.position = 'absolute';
       // document.getElementById("stats").appendChild(stats.domElement);
        window.addEventListener('resize', onWindowResize, true);
        $('.color-pic1').colorpicker();
        render();
        animate();
    });

    animate();

}

function generateTimeSeries(resultSets) {
    setupThreeJs();
    currentLoadedStart = 0;

    if(timeSeriesLength > MAX_PLOTS_STORED){
        loadPlotData(0,MAX_PLOTS_STORED)
        currentLoadedEnd = MAX_PLOTS_STORED
    }else{
        loadPlotData(0,timeSeriesLength)
        currentLoadedEnd = timeSeriesLength
    }
    initBufferAndLoad();

}

//TODO WInodow rezise does not work yet need to fix
function onWindowResize() {
    var width = window.innerWidth
    var height = window.innerHeight - 57 - 40 - 40 - 10;
    camera.aspect = width / height;
    camera.updateProjectionMatrix();
    renderer.setSize(width *0.9, height);
    //controls.handleResize();
    render();
}

function randomRBG(){
    return (Math.floor(Math.random() * (255 - 0 + 1)) + 0);
}

function loadPlotData(start,end){
    var cluster;
    var geometry = [];
    var hsl;

    for (var i = start; i < end; i++) {

        clusterUrl = "/resultssetall/" + resultSets[i].timeSeriesId + "/file/" + resultSets[i].id;
        $.getJSON(clusterUrl, function (data) {
            particles = {};
            colors = {};
            geometry = {};
            clusters = data.clusters;
            fileName = data.fileName;


            for (var i = 0; i < clusters.length; i++) {

                var clusterdata = data.clusters[i];
                var clusterid = clusterdata.clusterid;
                var clustercolor = clusterdata.color
                if(clustercolor == null)
                    clustercolor = {"a":randomRBG(),"b":randomRBG(),"g":randomRBG(),"r":randomRBG()}

                hsl = [heus[clusterid], 1, 0.8];
                if(!geometry.hasOwnProperty(clusterdata.clusterid)){
                    geometry[clusterdata.clusterid] = new THREE.Geometry();
                    colors[clusterdata.clusterid] = new Array();
                    particles[clusterdata.clusterid] = new Array();
                }
                colors[clusterdata.clusterid].push(new THREE.Color("rgb(" + clustercolor.r + "," + clustercolor.g + "," + clustercolor.b + ")"));

                if (!sections.hasOwnProperty(clusterdata.clusterid))
                    sections[clusterdata.clusterid] = {"length":clusterdata.points.length, "size":clusterdata.size,
                        "shape":clusterdata.shape, "visible":clusterdata.visible, "color": clustercolor, "label":clusterdata.label}

                for (var k in clusterdata.points) {
                    var p = clusterdata.points[k];

                    //var hsl = [heus[data.cid], 1, 0.8];
                    var vertex = new THREE.Vector3(p.x, p.y, p.z);
                    geometry[clusterid].vertices.push(vertex);

                    if(k == 0)
                        continue
                    colors[clusterdata.clusterid].push(new THREE.Color("rgb(" + clustercolor.r + "," + clustercolor.g + "," + clustercolor.b + ")"));

                }
            }
            for (var key in geometry) {
                if (geometry.hasOwnProperty(key)) {
                    colorlist[key] = colors[key][0].getHexString();
                    geometry[key].colors = colors[key];
                    particles[key] = new THREE.PointCloud(geometry[key], loadMatrial(sections[key].size,sections[key].shape, false));
                }
            }

            particleSets[data.timeSeriesSeqNumber] = particles;
            fileNames[data.timeSeriesSeqNumber] = data.fileName;
            console.log(data.fileName)
        });

    }
}


//Util functions
function initPlotData(){
    $("#slider").slider("option", "max", timeSeriesLength-1);
    $("#slider").slider("option", "value", $("#slider").slider("value"));
    currentParticles = particleSets["0"];
    for (var key in currentParticles) {
        if (currentParticles.hasOwnProperty(key)) {
            scene3d.add(currentParticles[key]);
        }
    }
    window.document.getElementById("cluster_table_div").innerHTML = generateCheckList(sections, colorlist);
    $('.color-pic1').colorpicker();
}

function initBufferAndLoad(){

    setTimeout(function () {
        if(Object.keys(particleSets).length < timeSeriesLength && Object.keys(particleSets).length < MAX_PLOTS_STORED){
            initBufferAndLoad();
        }else{
            if (currentLoadedStart in particleSets) {
                initPlotData();
                render();
                animate();
            }
        }
    }, 1000);
}

function gotoBufferAndLoad(event, ui){

    setTimeout(function () {
        if(Object.keys(particleSets).length < timeSeriesLength && Object.keys(particleSets).length < MAX_PLOTS_STORED){
            gotoBufferAndLoad(event, ui);
        }else{
            if (currentLoadedStart in particleSets) {
                updatePlot(event, ui)
                render();
                animate();
            }
        }
    }, 1000);
}

function setupThreeJs(){
    renderer = null;
    particles = [];
    colors = [];
    controls = null;
    var height = window.innerHeight - 57 - 40 - 40 - 10;
    $('#canvas3d').width(window.innerWidth*0.9);
    $('#canvas3d').height(height);
    var canvasWidth = $('#canvas3d').width();
    var canvasHeight = $('#canvas3d').height();

    //new THREE.Scene
    scene3d = new THREE.Scene();
    stats = new Stats();
    //set the scene
    var canvas3d = $('#canvas3d');
    //new THREE.WebGLRenderer
    renderer = new THREE.WebGLRenderer({canvas: canvas3d.get(0), antialias: true});
    renderer.setSize(canvasWidth, canvasHeight);
    renderer.setClearColor(0x121224, 1);

    //new THREE.PerspectiveCamera
    camera = new THREE.PerspectiveCamera(45, canvasWidth / canvasHeight, 0.1, 10000);
    camera.name = 'camera';
    camera.position.set(1, 1, 1);
    scene3d.add(camera);
    controls = new THREE.OrbitControls(camera, renderer.domElement);
   // stats.domElement.style.position = 'absolute';
    //document.getElementById("stats").appendChild(stats.domElement);
    window.addEventListener('resize', onWindowResize, false);
}

function loadMatrial(size, shape, isglyph){

    if(!isglyph){
        sprite = null;
    }else{
        sprite = THREE.ImageUtils.loadTexture(ImageEnum.BALL);
    }

    if(size>1){
       sprite = THREE.ImageUtils.loadTexture(ImageEnum.BALL);
    }

    var material = new THREE.PointCloudMaterial({
        size: size/200,
        map: sprite,
        vertexColors: THREE.VertexColors,
        transparent: true,
        opacity:0.9
    });
    return material;
}

ImageEnum = {
    BALL : "/assets/images/textures/ball.png",
    CUBE : "/assets/images/textures/ball.png",
}

function updatePlot(event, ui) {
    var sliderValue = ui.value;
    if(sliderValue >= currentLoadedStart && sliderValue < currentLoadedEnd){
        if (sliderValue in particleSets) {
            scene3d = new THREE.Scene();
            scene3d.add(camera);
            $("#slider").slider("option", "value", $("#slider").slider("value"));
            currentParticles = particleSets[sliderValue];

            for (var key in currentParticles) {
                if (currentParticles.hasOwnProperty(key)) {
                    if (recoloredclusters.hasOwnProperty(key)) {
                        currentParticles[key].geometry.colors = recoloredclusters[key];
                        currentParticles[key].geometry.colorsNeedUpdate = true;
                    }
                    if (!(removedclusters.hasOwnProperty(key))) {
                        scene3d.add(currentParticles[key]);
                    }
                }
            }
            window.addEventListener('resize', onWindowResize, false);
            render();
            animate();
            $("#amount").val(fileNames[sliderValue]);
        }
    }else {
        for(var k = 0; k < (currentLoadedEnd - currentLoadedStart); k++){
            delete particleSets[currentLoadedStart + k];
        }
        currentLoadedStart = sliderValue;
        currentLoadedEnd = timeSeriesLength
        if(timeSeriesLength > (currentLoadedStart + MAX_PLOTS_STORED)){
            currentLoadedEnd = currentLoadedStart + MAX_PLOTS_STORED;
        }
        //TODO check if this might fail in edge cases
        loadPlotData(currentLoadedStart, currentLoadedEnd)
        gotoBufferAndLoad(event, ui);
    }
}

function animate() {
    requestAnimationFrame(animate);
    controls.update();
    stats.update();
    render();
}

function render() {
    var camera = scene3d.getObjectByName('camera');
    renderer.render(scene3d, camera);
}

function removeSection(id) {
    scene3d.remove(currentParticles[id]);
    removedclusters[id] = id;
}

function addSection(id) {
    scene3d.add(currentParticles[id]);
    delete removedclusters[id];
}

function recolorSection(id, color) {
    colorlist[id] = color;

    for (var i in colors[id]) {
        colors[id][i] = new THREE.Color(color);
    }
    recoloredclusters[id] = colors[id];
    currentParticles[id].geometry.colors = colors[id];
    currentParticles[id].geometry.colorsNeedUpdate = true;
}

function animateTimeSeriesPlay(){
    isPlaying = true;
    isPaused = false;
    playLoop();
}

function playLoop() {
    var currentValue = $("#slider").slider("value");
    var maxValue = currentLoadedEnd-1;
    checkAndBufferData(currentValue+1)
    if(currentValue == maxValue){
        $('#slider-play').removeClass("fa fa-pause").addClass("fa fa-play-circle");
        return
    }
    if(currentValue > currentLoadedEnd){
        isPaused = true;
        isPlaying = false;
    }else {
        setTimeout(function () {
            scene3d = new THREE.Scene();
            scene3d.add(camera);
            $("#slider").slider("option", "value", currentValue + 1);
            currentParticles = particleSets[currentValue + 1];
            for (var key in currentParticles) {
                if (currentParticles.hasOwnProperty(key)) {
                    if (recoloredclusters.hasOwnProperty(key)) {
                        currentParticles[key].geometry.colors = recoloredclusters[key];
                        currentParticles[key].geometry.colorsNeedUpdate = true;
                    }
                    if (!(removedclusters.hasOwnProperty(key))) {
                        scene3d.add(currentParticles[key]);
                    }
                }
            }
            window.addEventListener('resize', onWindowResize, false);
            $("#amount").val(fileNames[currentValue + 1]);
            render();
            if (maxValue > currentValue + 1 && !isPaused) {
                playLoop();
            }else{
                isPaused = true;
                isPlaying = false;
                if(maxValue == currentValue + 1){
                    $('#slider-play').removeClass("fa fa-pause").addClass("fa fa-history");
                }
            }
        }, TIME_BETWEEN_PLOTS_IN_MILLS);
    }
}


function isBufferNeeded(currentval){
    if(currentLoadedEnd == timeSeriesLength){
        return false;
    }
    return (currentval == (currentLoadedStart + Math.floor((currentLoadedEnd - currentLoadedStart)/2))) ?  true : false;
}
function checkAndBufferData(currentval){
    if(isBufferNeeded(currentval)){
        var loadend = timeSeriesLength
        if(timeSeriesLength > currentLoadedEnd + LOAD_SIZE){
            loadend = currentLoadedEnd + LOAD_SIZE;
        }
        loadPlotData(currentLoadedEnd,loadend)
        for(var i =0; i < (loadend - currentLoadedEnd); i++){
            delete particleSets[currentLoadedStart+i];
        }
        checkIfBuffered();
    }
}

function checkIfBuffered(){
    setTimeout(function () {
        if(Object.keys(particleSets).length < timeSeriesLength && Object.keys(particleSets).length < MAX_PLOTS_STORED){
            checkIfBuffered();
        }else{
            currentLoadedStart += LOAD_SIZE;
            currentLoadedEnd += LOAD_SIZE;
            if(currentLoadedEnd > timeSeriesLength){
                currentLoadedEnd = timeSeriesLength
            }
            if(isPaused){
                isPaused = false;
                isPlaying = true;
                playLoop();

            }
        }
    }, 1000);
}

function  animateTimeSeriesPause(){
    isPaused = true
}

function resetView(){
    controls.reset();
}

function resetSlider(){
    isPaused = true;
    isPlaying = false;
    $("#slider").slider("option", "value", 0);
    var value ={"value":0}
    updatePlot(null,value);
}