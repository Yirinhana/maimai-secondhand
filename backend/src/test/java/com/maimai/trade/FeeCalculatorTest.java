package com.maimai.trade;
import com.maimai.common.FeeCalculator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;
class FeeCalculatorTest {
    @ParameterizedTest
    @CsvSource({"0,0","1000,0","1666,0","1667,1","5000,2","10000,3","100000,30"})
    void halfUpFeeInCentsAllowsZero(long goods,long expected) {
        assertThat(FeeCalculator.platformFee(goods)).isEqualTo(expected);
    }
}
