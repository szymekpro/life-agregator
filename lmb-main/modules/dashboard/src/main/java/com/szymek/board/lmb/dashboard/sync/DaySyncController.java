package com.szymek.board.lmb.dashboard.sync;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DaySyncController {

    private final DaySyncService syncService;

    public DaySyncController(DaySyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/sync")
    public DaySyncResult sync(
            @RequestParam(required = false) Integer days,
            @RequestParam(defaultValue = "false") boolean force) {
        return syncService.sync(days, force);
    }
}
