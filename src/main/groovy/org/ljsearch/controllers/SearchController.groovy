package org.ljsearch.controllers

import groovy.json.JsonBuilder
import org.ljsearch.lucene.ISeacher
import org.ljsearch.lucene.Post
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

/**
 * Created by Pavel on 10/5/2015.
 */
@Controller
class SearchController {

    @Autowired
    protected ISeacher seacher


    @RequestMapping("/search")
    @ResponseBody String search(
            @RequestParam("term") String term,
            @RequestParam(value = "journal", required = false) String journal,
            @RequestParam(value = "poster", required = false) String poster
    ){
        def results = seacher.search(journal, poster, term)
        return new JsonBuilder( results).toPrettyString()
    }
}
