/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package es.us.sedl.analyzer.computestats;

import com.google.common.collect.Lists;
import es.us.isa.jdataset.writer.PlainTextWriter;
import es.us.isa.labs.apps.statservice.schemas.nonparametric.multiplecomparisontest.NonParametricMultipleComparisonInput;

import es.us.isa.labs.apps.statservice.schemas.parametric.multiplecomparisontest.ParametricMultipleComparisonInput;
import es.us.isa.labs.apps.statservice.schemas.statsbasedata.DataSet;
import es.us.isa.labs.apps.statservice.schemas.statsbasedata.PValue;
import es.us.isa.labs.apps.statservice.schemas.statsbasedata.StatisticalTestInput;
import es.us.isa.labs.apps.statservice.schemas.statsbasedata.StatisticalTestResult;
import es.us.isa.labs.apps.statservice.wsdls.homoscedasticity.homoscedasticitytest.LeveneFault;
import es.us.isa.labs.apps.statservice.wsdls.nonparametric.multiplecomparisontest.FriedmanFault;
import es.us.isa.labs.apps.statservice.wsdls.parametric.multiplecomparisontest.ANOVAFault;
import es.us.isa.labs.apps.statservice.wsdls.nonparametric.singlecomparisontest.WilcoxonFault;
import es.us.isa.labs.apps.statservice.wsdls.normality.normalitytest.KolmogorovSmirnovFault;
import es.us.isa.labs.apps.statservice.wsdls.normality.normalitytest.ShapiroWilkFault;
import es.us.isa.labs.apps.statservice.wsdls.parametric.singlecomparisontest.TTestFault;
import es.us.isa.labs.statservice.webservice.nonparametric.NonParametricMultipleComparisonService;
import es.us.isa.labs.statservice.webservice.nonparametric.NonParametricSingleComparisonService;
import es.us.isa.labs.statservice.webservice.parametric.ParametricMultiComparisonService;
import es.us.isa.labs.statservice.webservice.parametric.ParametricSingleComparisonService;
import es.us.isa.labs.statservice.webservice.normality.NormalityTestService;
import es.us.isa.labs.statservice.webservice.homoscedasticity.HomoscedasticityTestService;
import es.us.isa.sedl.analysis.operations.information.computestats.StatisticalAnalysisOperation;
import es.us.isa.sedl.analysis.operations.information.computestats.StatisticsComputingEngine;
import es.us.isa.sedl.analysis.operations.information.computestats.UnsupportedStatisticException;
import es.us.isa.sedl.analysis.operations.information.computestats.engine.StatisticComputingEnginePlugin;
import es.us.isa.sedl.core.BasicExperiment;
import es.us.isa.sedl.core.util.Error;
import es.us.isa.sedl.core.analysis.statistic.NHST;
import es.us.isa.sedl.core.analysis.statistic.Statistic;
import es.us.isa.sedl.core.analysis.statistic.StatisticalAnalysisResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static es.us.isa.sedl.analysis.operations.information.computestats.engine.ExtensibleStatisticsComputingEngine.token;
import static es.us.isa.sedl.grammar.SEDL4PeopleLexer.*;
import es.us.isa.sedl.runtime.analysis.validation.ValidationError;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author José Antonio Parejo
 */
public class StatisticalTestsComputingEngine implements StatisticComputingEnginePlugin {

    List<String> supportedTests = Lists.newArrayList(
            token(T_STUDENT),
            token(ANOVA),
            token(WILCOXON),
            token(FRIEDMAN),
            token(KOLMOGOROV_SMIRNOV),
            token(SHAPIRO_WILK),
            token(LEVENE)
    );

    String COLUMN_SEPARATOR = ";";
    String ROW_SEPARATOR = "\n";

    @Override
    public boolean isSupported(Statistic s) {
        if (s instanceof NHST) {
            NHST nhst = (NHST) s;
            for (String supportedTest : supportedTests) {
                if (nhst.getName().equalsIgnoreCase(supportedTest)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public List<StatisticalAnalysisResult> compute(StatisticalAnalysisOperation operation) throws UnsupportedStatisticException {
        List<StatisticalAnalysisResult> result = Collections.EMPTY_LIST;
        NHST test = (NHST) operation.getStatistic();
        String testName = test.getName();

        if (token(T_STUDENT).equalsIgnoreCase(testName)) {
            result=computeTStudent(operation, test);
        } else if (token(ANOVA).equalsIgnoreCase(testName)) {
            result=computeANOVA(operation, test);
        } else if (token(WILCOXON).equalsIgnoreCase(testName)) {
            result=computeWILCOXON(operation, test);
        } else if (token(FRIEDMAN).equalsIgnoreCase(testName)) {
            result=computeFRIEDMAN(operation, test);
        }else if (token(KOLMOGOROV_SMIRNOV).equalsIgnoreCase(testName)) {
            result=computeKOLMOGORV_SMIRNOV(operation, test);
        }else if (token(SHAPIRO_WILK).equalsIgnoreCase(testName)) {
            result=computeSHAPIRO_WILK(operation, test);
        }else if (token(LEVENE).equalsIgnoreCase(testName)) {
            result=computeLEVENE(operation, test);
        } else {
            throw new UnsupportedStatisticException(test, this);
        }
        return result;
    }

    private List<StatisticalAnalysisResult> computeTStudent(StatisticalAnalysisOperation operation, NHST test) {
        List<StatisticalAnalysisResult> result = new ArrayList<StatisticalAnalysisResult>();
        StatisticalTestInput s = new StatisticalTestInput();
        DataSet dataset = generateDatasetContent(operation.getDataset(), ROW_SEPARATOR, COLUMN_SEPARATOR);
        s.setDataSet(dataset);
        ParametricSingleComparisonService pscs = new ParametricSingleComparisonService();
        try {
            StatisticalTestResult str = pscs.tTest(s);
            extractOutput(str, operation, test, result);
        } catch (TTestFault e) {
            ValidationError<BasicExperiment> ve = new ValidationError<BasicExperiment>(null, Error.ERROR_SEVERITY.ERROR, "[ERROR] T-Student operation not support datasets with more than two variables");
            operation.getErrors().add(ve);
            e.printStackTrace();
        }
        return result;
    }

    private DataSet generateDatasetContent(es.us.isa.jdataset.DataSet dataset, String ROW_SEPARATOR, String COLUMN_SEPARATOR) {
        PlainTextWriter writer = new PlainTextWriter(COLUMN_SEPARATOR, ROW_SEPARATOR);
        DataSet result = new DataSet();
        result.setValue(writer.write(dataset));
        result.setCaseLabelsInFirstColumn(false);
        result.setColumnSeparator(COLUMN_SEPARATOR);
        result.setDecimalNumberSeparator(".");
        result.setRowSeparator(ROW_SEPARATOR);
        result.setVariableNamesInFirstRow(true);
        return result;
    }

    private List<StatisticalAnalysisResult> computeANOVA(StatisticalAnalysisOperation operation, NHST test) {
        List<StatisticalAnalysisResult> result = new ArrayList<StatisticalAnalysisResult>();
        DataSet dataset = generateDatasetContent(operation.getDataset(), ROW_SEPARATOR, COLUMN_SEPARATOR);
        ParametricMultipleComparisonInput input = new ParametricMultipleComparisonInput();
        input.setDataSet(dataset);
        input.setLatexOutput(false);
        input.setPostHocAnalyses(null);
        ParametricMultiComparisonService pmcs = new ParametricMultiComparisonService();
        try {
            StatisticalTestResult str = pmcs.anova(input);
            extractOutput(str, operation, test, result);
        } catch (ANOVAFault e) {
            ValidationError<BasicExperiment> ve = new ValidationError<BasicExperiment>(null, Error.ERROR_SEVERITY.ERROR, "[ERROR] " + e.getMessage());
            operation.getErrors().add(ve);
            e.printStackTrace();
        }
        return result;
    }

    private List<StatisticalAnalysisResult> computeWILCOXON(StatisticalAnalysisOperation operation, NHST test) {

        List<StatisticalAnalysisResult> result = new ArrayList<StatisticalAnalysisResult>();
        DataSet dataset = generateDatasetContent(operation.getDataset(), ROW_SEPARATOR, COLUMN_SEPARATOR);
        NonParametricMultipleComparisonInput input = new NonParametricMultipleComparisonInput();
        input.setDataSet(dataset);
        input.setLatexOutput(false);
        input.setPostHocAnalyses(null);
        NonParametricSingleComparisonService pmcs = new NonParametricSingleComparisonService();
        try {
            StatisticalTestResult str = pmcs.wilcoxon(input);
            extractOutput(str, operation, test, result);
        } catch (WilcoxonFault ex) {
            Logger.getLogger(StatisticalTestsComputingEngine.class.getName()).log(Level.SEVERE, null, ex);
        }
        return result;
    }

    private List<StatisticalAnalysisResult> computeFRIEDMAN(StatisticalAnalysisOperation operation, NHST test) {
        List<StatisticalAnalysisResult> result = new ArrayList<StatisticalAnalysisResult>();
        DataSet dataset = generateDatasetContent(operation.getDataset(), ROW_SEPARATOR, COLUMN_SEPARATOR);
        NonParametricMultipleComparisonInput input = new NonParametricMultipleComparisonInput();
        input.setDataSet(dataset);
        input.setLatexOutput(false);
        input.setPostHocAnalyses(null);
        NonParametricMultipleComparisonService pmcs = new NonParametricMultipleComparisonService();
        try {
            StatisticalTestResult str = pmcs.friedman(input);
            extractOutput(str, operation, test, result);        
        } catch (FriedmanFault ex) {
            Logger.getLogger(StatisticalTestsComputingEngine.class.getName()).log(Level.SEVERE, null, ex);
        }
        return result;
    }

    private void extractOutput(StatisticalTestResult str, StatisticalAnalysisOperation operation, NHST test, List<StatisticalAnalysisResult> result) {
        es.us.isa.sedl.core.analysis.statistic.PValue pvalue = null;
        List<PValue> values = str.getSignificanceResult().getPvalues();
        for (PValue pv : values) {
            pvalue = new es.us.isa.sedl.core.analysis.statistic.PValue();
            pvalue.setValue(pv.getValue());
            pvalue.setSignificanceThreshold(test.getAlpha());
            pvalue.setNHST(((NHST) operation.getStatistic()).getName());
            pvalue.setDatasetSpecification(operation.getStatistic().getDatasetSpecification());
            result.add(pvalue);
        }
    }

    private List<StatisticalAnalysisResult> computeKOLMOGORV_SMIRNOV(StatisticalAnalysisOperation operation, NHST test) {
        List<StatisticalAnalysisResult> result = new ArrayList<StatisticalAnalysisResult>();
        DataSet dataset = generateDatasetContent(operation.getDataset(), ROW_SEPARATOR, COLUMN_SEPARATOR);
        NonParametricMultipleComparisonInput input = new NonParametricMultipleComparisonInput();
        input.setDataSet(dataset);
        input.setLatexOutput(false);
        input.setPostHocAnalyses(null);
        NormalityTestService pmcs = new NormalityTestService();
        try {
            StatisticalTestResult str = pmcs.kolmogorovSmirnov(input);
            extractOutput(str, operation, test, result);        
        } catch (KolmogorovSmirnovFault ex) {
            Logger.getLogger(StatisticalTestsComputingEngine.class.getName()).log(Level.SEVERE, null, ex);
        }
        return result;
    }

    private List<StatisticalAnalysisResult> computeLEVENE(StatisticalAnalysisOperation operation, NHST test) {
         List<StatisticalAnalysisResult> result = new ArrayList<StatisticalAnalysisResult>();
        DataSet dataset = generateDatasetContent(operation.getDataset(), ROW_SEPARATOR, COLUMN_SEPARATOR);
        StatisticalTestInput input = new StatisticalTestInput();
        input.setDataSet(dataset);
        input.setLatexOutput(false);        
        HomoscedasticityTestService tcs = new HomoscedasticityTestService();
        try {
            StatisticalTestResult str = tcs.levene(input);
            extractOutput(str, operation, test, result);        
        } catch (LeveneFault ex) {
            Logger.getLogger(StatisticalTestsComputingEngine.class.getName()).log(Level.SEVERE, null, ex);
        }
        return result;
    }

    private List<StatisticalAnalysisResult> computeSHAPIRO_WILK(StatisticalAnalysisOperation operation, NHST test) {
        List<StatisticalAnalysisResult> result = new ArrayList<StatisticalAnalysisResult>();
        DataSet dataset = generateDatasetContent(operation.getDataset(), ROW_SEPARATOR, COLUMN_SEPARATOR);
        NonParametricMultipleComparisonInput input = new NonParametricMultipleComparisonInput();
        input.setDataSet(dataset);
        input.setLatexOutput(false);
        input.setPostHocAnalyses(null);
        NormalityTestService pmcs = new NormalityTestService();
        try {
            StatisticalTestResult str = pmcs.shapiroWilk(input);
            extractOutput(str, operation, test, result);        
        } catch (ShapiroWilkFault ex) {
            Logger.getLogger(StatisticalTestsComputingEngine.class.getName()).log(Level.SEVERE, null, ex);
        }
        return result;
    }

    @Override
    public Class<? extends Statistic> supportedStatistic() {
        return NHST.class;
    }

}
