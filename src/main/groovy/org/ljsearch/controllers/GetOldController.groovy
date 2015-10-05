package org.ljsearch.controllers

import org.ljsearch.entity.IJournalRepository
import org.ljsearch.services.Downloading
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping

/**
 * Created by Pavel on 10/5/2015.
 */
@Controller
class GetOldController {
    @Autowired
    ApplicationContext context
    @Autowired
    IJournalRepository repo


    @RequestMapping("/get")
    def get(){
        def downloader = context.getBean(Downloading.class,repo.findOne("tourism_il"))
        downloader.run()
    }
}
