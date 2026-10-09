package com.szymek.board.lmb.core;

import com.szymek.board.lmb.dashboard.sync.DayDataRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class LmbCoreApplicationTests {

    // The test context runs without a database, so the JDBC-backed repository is replaced.
    @MockitoBean
    DayDataRepository dayDataRepository;

    @Test
    void contextLoads() {
    }

}
