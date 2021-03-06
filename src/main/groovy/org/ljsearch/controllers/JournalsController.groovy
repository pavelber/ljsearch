package org.ljsearch.controllers

import groovy.json.JsonBuilder
import org.ljsearch.entity.IJournalRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody


@Controller
class JournalsController {

    @Autowired
    IJournalRepository repo


    @RequestMapping("/journals")
    @ResponseBody String get() {
        def journals = repo.findByPrrivate(false)
        journals.each {it.user = null}
        return   new JsonBuilder( journals).toPrettyString()
    }
}
