package controllers;


import db.GroupsDAO;
import db.SearchDAO;
import db.TagsDAO;
import models.Group;
import models.TimeSeries;
import models.User;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;
import views.html.dashboard;
import views.html.tagsearch;

import java.util.List;

public class SearchController extends Controller {
    @Security.Authenticated(Secured.class)
    public static Result searchArtifactsByTag(String tagname) {
        User loggedInUser = User.findByEmail(request().username());
        List<TimeSeries> taggedtimeseries = SearchDAO.getArtifactsByTag(loggedInUser.email,tagname);
        String[] tagslist = {tagname};
        if (taggedtimeseries != null) {
            return ok(dashboard.render(loggedInUser, false, null, taggedtimeseries, GroupsDAO.allGroups(loggedInUser.email), false, true, tagslist, null, false, "Dashboard"));
        } else {
            return badRequest("{status: 'fail'}").as("application/json");
        }
    }

    public static Result searchArtifactsByTagPublic(String tagname) {
        User loggedInUser = User.findByEmail(request().username());
        List<TimeSeries> tags = SearchDAO.getArtifactsByTag(null,tagname);
        if (tags != null) {
            return ok().as("application/json");
        } else {
            return badRequest("{status: 'fail'}").as("application/json");
        }
    }
}
