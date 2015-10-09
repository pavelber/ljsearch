package org.ljsearch.controllers

import groovy.json.JsonBuilder
import org.ljsearch.entity.IJournalRepository
import org.ljsearch.entity.INewsRepository
import org.ljsearch.services.Downloading
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody

/**
 * Created by Pavel on 10/5/2015.
 */
@Controller
class NewsController {

    @Autowired
    INewsRepository repo


    @RequestMapping("/news")
    @ResponseBody String get() {
        def news = repo.findAll()
         return   new JsonBuilder( news).toPrettyString()
    }
}
