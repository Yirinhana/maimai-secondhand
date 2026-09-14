package com.maimai.trade;
import com.maimai.payment.finance.FinanceService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class FinanceCsvTest {
    @ParameterizedTest @ValueSource(strings={"=HYPERLINK(1)","+cmd","-1+2","@SUM(1)","   =1+1","\tformula","\rformula"})
    void spreadsheetCommandsAreText(String value){assertThat(FinanceService.csvCell(value)).startsWith("\"'");}
    @Test void quotesAndCommasStayInOneCell(){assertThat(FinanceService.csvCell("普通,\"文本\"")).isEqualTo("\"普通,\"\"文本\"\"\"");}
}
