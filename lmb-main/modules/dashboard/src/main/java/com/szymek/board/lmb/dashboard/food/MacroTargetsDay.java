package com.szymek.board.lmb.dashboard.food;

import com.szymek.board.lmb.dashboard.sync.DayPayload;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MacroTargetsDay implements DayPayload {

    private String mode;
    private Double calories;
    private Double proteinG;
    private Double fatG;
    private Double carbohydrateG;
}
