package org.ljsearch.controllers

import groovy.json.JsonBuilder
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody

/**
 * Created by Pavel on 10/9/2015.
 */
@Controller
class YearsController {

    @RequestMapping("/years")
    @ResponseBody String get() {
        int year = Calendar.getInstance().get(Calendar.YEAR)
        def years = (2001..year).collect()
        return   new JsonBuilder( years).toPrettyString()
    }
}
