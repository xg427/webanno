/*******************************************************************************
 * Copyright 2012
 * Ubiquitous Knowledge Processing (UKP) Lab and FG Language Technology
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package de.tudarmstadt.ukp.clarin.webanno.brat.util;

import static de.tudarmstadt.ukp.clarin.webanno.brat.controller.BratAjaxCasUtil.selectByAddr;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.uima.UIMAException;
import org.apache.uima.cas.Type;
import org.apache.uima.jcas.JCas;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.codehaus.jackson.JsonGenerator;
import org.springframework.beans.BeansException;
import org.springframework.http.converter.json.MappingJacksonHttpMessageConverter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.uimafit.util.JCasUtil;

import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationService;
import de.tudarmstadt.ukp.clarin.webanno.api.RepositoryService;
import de.tudarmstadt.ukp.clarin.webanno.brat.annotation.BratAnnotator;
import de.tudarmstadt.ukp.clarin.webanno.brat.annotation.BratAnnotatorModel;
import de.tudarmstadt.ukp.clarin.webanno.brat.controller.AnnotationTypeConstant;
import de.tudarmstadt.ukp.clarin.webanno.brat.controller.BratAjaxCasController;
import de.tudarmstadt.ukp.clarin.webanno.brat.controller.BratAjaxCasUtil;
import de.tudarmstadt.ukp.clarin.webanno.brat.controller.BratAnnotationException;
import de.tudarmstadt.ukp.clarin.webanno.brat.curation.AnnotationOption;
import de.tudarmstadt.ukp.clarin.webanno.brat.curation.AnnotationSelection;
import de.tudarmstadt.ukp.clarin.webanno.brat.curation.CasDiff;
import de.tudarmstadt.ukp.clarin.webanno.brat.curation.component.CurationSegmentPanel;
import de.tudarmstadt.ukp.clarin.webanno.brat.curation.component.model.AnnotationState;
import de.tudarmstadt.ukp.clarin.webanno.brat.curation.component.model.CurationBuilder;
import de.tudarmstadt.ukp.clarin.webanno.brat.curation.component.model.CurationContainer;
import de.tudarmstadt.ukp.clarin.webanno.brat.curation.component.model.CurationSegmentForSourceDocument;
import de.tudarmstadt.ukp.clarin.webanno.brat.curation.component.model.CurationUserSegmentForAnnotationDocument;
import de.tudarmstadt.ukp.clarin.webanno.brat.display.model.Argument;
import de.tudarmstadt.ukp.clarin.webanno.brat.display.model.Entity;
import de.tudarmstadt.ukp.clarin.webanno.brat.display.model.Relation;
import de.tudarmstadt.ukp.clarin.webanno.brat.display.model.RelationType;
import de.tudarmstadt.ukp.clarin.webanno.brat.message.GetDocumentResponse;
import de.tudarmstadt.ukp.clarin.webanno.brat.project.ProjectUtil;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocument;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocumentState;
import de.tudarmstadt.ukp.clarin.webanno.model.Mode;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.clarin.webanno.model.User;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;

/**
 * A utility class for the curation AND Correction modules
 *
 * @author Seid Muhie Yimam
 *
 */
public class BratCuratorUtility
{
    public final static String CURATION_USER = "CURATION_USER";

    /**
     * Get JCAS objects of annotator where {@link CasDiff} will run on it
     *
     * @throws IOException
     * @throws ClassNotFoundException
     * @throws UIMAException
     */
    public static void getCases(Map<String, JCas> aJCases,
            List<AnnotationDocument> aAnnotationDocuments, RepositoryService aRepository,
            Map<String, Map<Integer, AnnotationSelection>> annotationSelectionByUsernameAndAddress)
        throws UIMAException, ClassNotFoundException, IOException
    {
        for (AnnotationDocument annotationDocument : aAnnotationDocuments) {
            String username = annotationDocument.getUser();
            if (annotationDocument.getState().equals(AnnotationDocumentState.FINISHED)
                    || username.equals(CURATION_USER)) {
                JCas jCas = aRepository.getAnnotationDocumentContent(annotationDocument);
                aJCases.put(username, jCas);

                // cleanup annotationSelections
                annotationSelectionByUsernameAndAddress.put(username,
                        new HashMap<Integer, AnnotationSelection>());
            }
        }
    }

    /**
     * Set different attributes for {@link BratAnnotatorModel} that will be used for the
     * {@link CurationSegmentForSourceDocument}
     *
     * @throws IOException
     * @throws FileNotFoundException
     * @throws BeansException
     */

    public static BratAnnotatorModel setBratAnnotatorModel(SourceDocument aSourceDocument,
            RepositoryService aRepository, CurationSegmentForSourceDocument aCurationSegment,
            AnnotationService aAnnotationService)
        throws BeansException, FileNotFoundException, IOException
    {
        User userLoggedIn = aRepository.getUser(SecurityContextHolder.getContext()
                .getAuthentication().getName());
        BratAnnotatorModel bratAnnotatorModel = new BratAnnotatorModel();// .getModelObject();
        bratAnnotatorModel.setDocument(aSourceDocument);
        bratAnnotatorModel.setProject(aSourceDocument.getProject());
        bratAnnotatorModel.setUser(userLoggedIn);
        bratAnnotatorModel.setFirstSentenceAddress(aCurationSegment.getSentenceAddress().get(
                CURATION_USER));
        bratAnnotatorModel.setLastSentenceAddress(aCurationSegment.getSentenceAddress().get(
                CURATION_USER));
        bratAnnotatorModel.setSentenceAddress(aCurationSegment.getSentenceAddress().get(
                CURATION_USER));

        bratAnnotatorModel.setSentenceBeginOffset(aCurationSegment.getBegin());
        bratAnnotatorModel.setSentenceEndOffset(aCurationSegment.getEnd());

        bratAnnotatorModel.setMode(Mode.CURATION);
        ProjectUtil.setAnnotationPreference(userLoggedIn.getUsername(),
                aRepository, aAnnotationService, bratAnnotatorModel, Mode.CURATION);
        return bratAnnotatorModel;
    }

    public static void fillLookupVariables(
            List<AnnotationOption> aAnnotationOptions,
            Map<String, Map<Integer, AnnotationSelection>> aAnnotationSelectionByUsernameAndAddress,
            BratAnnotatorModel bratAnnotatorModel)
    {
        // fill lookup variable for annotation selections
        for (AnnotationOption annotationOption : aAnnotationOptions) {
            for (AnnotationSelection annotationSelection : annotationOption
                    .getAnnotationSelections()) {
                for (String username : annotationSelection.getAddressByUsername().keySet()) {
                    if ((!username.equals(CURATION_USER) && bratAnnotatorModel.getMode().equals(
                            Mode.CURATION))
                            || (username.equals(CURATION_USER) && bratAnnotatorModel.getMode()
                                    .equals(Mode.CORRECTION))) {
                        Integer address = annotationSelection.getAddressByUsername().get(username);
                        // aAnnotationSelectionByUsernameAndAddress.put(username,
                        // new
                        // HashMap<Integer, AnnotationSelection>());
                        aAnnotationSelectionByUsernameAndAddress.get(username).put(address,
                                annotationSelection);
                    }
                }
            }
        }
    }

    public static void populateCurationSentences(
            Map<String, JCas> aJCases,
            List<CurationUserSegmentForAnnotationDocument> aSentences,
            BratAnnotatorModel aBratAnnotatorModel,
            List<AnnotationOption> aAnnotationOptions,
            Map<String, Map<Integer, AnnotationSelection>> aAnnotationSelectionByUsernameAndAddress,
            MappingJacksonHttpMessageConverter aJsonConverter)
        throws IOException
    {
        List<String> usernamesSorted = new LinkedList<String>(aJCases.keySet());
        Collections.sort(usernamesSorted);
        int numUsers = aJCases.size();
        for (String username : usernamesSorted) {
            if ((!username.equals(CURATION_USER) && aBratAnnotatorModel.getMode().equals(
                    Mode.CURATION))
                    || (username.equals(CURATION_USER) && aBratAnnotatorModel.getMode().equals(
                            Mode.CORRECTION))) {
                Map<Integer, AnnotationSelection> annotationSelectionByAddress = new HashMap<Integer, AnnotationSelection>();

                for (AnnotationOption annotationOption : aAnnotationOptions) {
                    for (AnnotationSelection annotationSelection : annotationOption
                            .getAnnotationSelections()) {
                        if (annotationSelection.getAddressByUsername().containsKey(username)) {
                            Integer address = annotationSelection.getAddressByUsername().get(
                                    username);
                            annotationSelectionByAddress.put(address, annotationSelection);
                        }
                    }
                }

                JCas jCas = aJCases.get(username);

                JCas userJCas = null;
                String logedUsername = SecurityContextHolder.getContext().getAuthentication()
                        .getName();
                int sentenceAddress = aBratAnnotatorModel.getSentenceAddress();
                int lastSentenceAddress = aBratAnnotatorModel.getLastSentenceAddress();
                if (aBratAnnotatorModel.getMode().equals(Mode.CORRECTION)) {
                    userJCas = aJCases.get(logedUsername);

                    aBratAnnotatorModel.setSentenceAddress(getSentenceAddress(aBratAnnotatorModel,
                            jCas, userJCas));
                    aBratAnnotatorModel.setLastSentenceAddress(getLastSentenceAddress(
                            aBratAnnotatorModel, jCas, userJCas));
                }
                else if (aBratAnnotatorModel.getMode().equals(Mode.CURATION)) {
                    userJCas = aJCases.get(CURATION_USER);

                    aBratAnnotatorModel.setSentenceAddress(getSentenceAddress(aBratAnnotatorModel,
                            jCas, userJCas));
                    aBratAnnotatorModel.setLastSentenceAddress(getLastSentenceAddress(
                            aBratAnnotatorModel, jCas, userJCas));
                }

                GetDocumentResponse response = new GetDocumentResponse();

                BratAjaxCasController
                        .addBratResponses(response, aBratAnnotatorModel, 0, jCas, true);

                CurationUserSegmentForAnnotationDocument curationUserSegment2 = new CurationUserSegmentForAnnotationDocument();
                curationUserSegment2.setCollectionData(getStringCollectionData(response, jCas,
                        annotationSelectionByAddress, username, numUsers, aJsonConverter,
                        aBratAnnotatorModel, aAnnotationOptions));
                curationUserSegment2.setDocumentResponse(getStringDocumentResponse(response,
                        aJsonConverter));
                curationUserSegment2.setUsername(username);
                curationUserSegment2.setBratAnnotatorModel(aBratAnnotatorModel);
                curationUserSegment2
                        .setAnnotationSelectionByUsernameAndAddress(aAnnotationSelectionByUsernameAndAddress);

                aSentences.add(curationUserSegment2);
                aBratAnnotatorModel.setSentenceAddress(sentenceAddress);
                aBratAnnotatorModel.setLastSentenceAddress(lastSentenceAddress);
            }
        }
    }

    /**
     * Get the sentence address for jCas from userJCas.
     *
     * @param aBratAnnotatorModel
     * @param jCas
     * @param userJCas
     * @return
     */
    private static int getSentenceAddress(BratAnnotatorModel aBratAnnotatorModel, JCas jCas,
            JCas userJCas)
    {
        int sentenceAddress = BratAjaxCasUtil.selectSentenceAt(userJCas,
                aBratAnnotatorModel.getSentenceBeginOffset(),
                aBratAnnotatorModel.getSentenceEndOffset()).getAddress();
        Sentence sentence = selectByAddr(userJCas, Sentence.class, sentenceAddress);
        List<Sentence> sentences = JCasUtil.selectCovered(jCas, Sentence.class,
                sentence.getBegin(), sentence.getEnd());
        return sentences.get(0).getAddress();
    }

    private static int getLastSentenceAddress(BratAnnotatorModel aBratAnnotatorModel, JCas jCas,
            JCas userJCas)
    {
        Sentence sentence = (Sentence) userJCas.getLowLevelCas().ll_getFSForRef(
                aBratAnnotatorModel.getLastSentenceAddress());
        List<Sentence> sentences = JCasUtil.selectCovered(jCas, Sentence.class,
                sentence.getBegin(), sentence.getEnd());
        return sentences.get(0).getAddress();
    }

    private static String getStringDocumentResponse(GetDocumentResponse aResponse,
            MappingJacksonHttpMessageConverter aJsonConverter)
        throws IOException
    {
        String docData = "{}";
        // Serialize BRAT object model to JSON
        StringWriter out = new StringWriter();
        JsonGenerator jsonGenerator = aJsonConverter.getObjectMapper().getJsonFactory()
                .createJsonGenerator(out);
        jsonGenerator.writeObject(aResponse);
        docData = out.toString();
        return docData;
    }

    private static String getStringCollectionData(GetDocumentResponse response, JCas jCas,
            Map<Integer, AnnotationSelection> annotationSelectionByAddress, String username,
            int numUsers, MappingJacksonHttpMessageConverter aJsonConverter,
            BratAnnotatorModel aBratAnnotatorModel, List<AnnotationOption> aAnnotationOptions)
        throws IOException
    {
        Map<String, Map<String, Object>> entityTypes = new HashMap<String, Map<String, Object>>();

        getEntityTypes(response, annotationSelectionByAddress, numUsers, entityTypes,
                aAnnotationOptions, aBratAnnotatorModel.getMode());

        Map<Object, Object> collection = new HashMap<Object, Object>();
        collection.put("entity_types", entityTypes.values());

        String collData = "{}";
        StringWriter out = new StringWriter();
        JsonGenerator jsonGenerator = aJsonConverter.getObjectMapper().getJsonFactory()
                .createJsonGenerator(out);
        jsonGenerator.writeObject(collection);
        collData = out.toString();
        return collData;
    }

    private static void getEntityTypes(GetDocumentResponse response,
            Map<Integer, AnnotationSelection> annotationSelectionByAddress, int numUsers,
            Map<String, Map<String, Object>> entityTypes,
            List<AnnotationOption> aAnnotationOptions, Mode aMode)
    {
        Map<Integer, String> targetAnnotations = new HashMap<Integer, String>();
        for (Entity entity : response.getEntities()) {
            int address = entity.getId();
            AnnotationSelection annotationSelection = annotationSelectionByAddress.get(address);
            AnnotationState newState = null;
            if (aMode.equals(Mode.CORRECTION)) {
                newState = getCorrectionState(annotationSelection, aAnnotationOptions, numUsers,
                        address);
            }
            else {
                newState = getCurationState(numUsers, annotationSelection);
            }
            targetAnnotations.put(entity.getId(), entity.getType() + "_(" + newState.name() + ")");
        }
        for (Entity entity : response.getEntities()) {
            // check if either address of entity has no changes ...
            // ... or if entity has already been clicked on
            int address = entity.getId();
            AnnotationSelection annotationSelection = annotationSelectionByAddress.get(address);
            AnnotationState newState = null;
            if (aMode.equals(Mode.CORRECTION)) {
                newState = getCorrectionState(annotationSelection, aAnnotationOptions, numUsers,
                        address);
            }
            else {
                newState = getCurationState(numUsers, annotationSelection);
            }
            if (newState != null) {
                String type = entity.getType() + "_(" + newState.name() + ")";
                String label = entity.getType();
                // FIXME WFT? Can we use TypeUtil.getLabel() here?! -- REC 2013-11-02
                label = label.replace(AnnotationTypeConstant.POS_PREFIX, "")
                        .replace(AnnotationTypeConstant.NAMEDENTITY_PREFIX, "")
                        .replace(AnnotationTypeConstant.COREFRELTYPE_PREFIX, "");
                entity.setType(type);
                boolean hasArc = false;
                for (Relation relation : response.getRelations()) {
                    Argument argument = relation.getArguments().get(0);
                    if (argument.getToken() == entity.getId()) {// has outgoing
                                                                // arc
                        hasArc = true;
                        List<RelationType> relations = getRelationTypes(response,
                                annotationSelectionByAddress, numUsers, relation,
                                targetAnnotations.get(relation.getArguments().get(1).getToken()),
                                aAnnotationOptions, aMode, entity);
                        Map<String, Object> enityTypeWithArcs = BratCuratorUtility.getEntity(type,
                                label, newState);
                        if (entityTypes.get(type) == null
                                || (entityTypes.get(type) != null && entityTypes.get(type).get(
                                        "arcs") == null)) {
                            enityTypeWithArcs.put("arcs", relations);
                            entityTypes.put(type, enityTypeWithArcs);
                        }
                        else {
                            List<RelationType> enityTypeWithArcsOld = (List<RelationType>) entityTypes
                                    .get(type).get("arcs");
                            enityTypeWithArcsOld.addAll(relations);
                            enityTypeWithArcs.put("arcs", enityTypeWithArcsOld);
                            entityTypes.put(type, enityTypeWithArcs);
                        }

                    }
                }
                if (!hasArc) {
                    if (entityTypes.get(type) == null
                            || (entityTypes.get(type) != null && entityTypes.get(type).get("arcs") == null)) {
                        entityTypes.put(type, BratCuratorUtility.getEntity(type, label, newState));
                    }
                }
            }

        }
    }

    private static AnnotationState getCurationState(int numUsers,
            AnnotationSelection annotationSelection)
    {
        AnnotationState newState;
        if (annotationSelection == null) {
            newState = AnnotationState.AGREE;

        }
        else if (annotationSelection.getAddressByUsername().size() == numUsers) {
            newState = AnnotationState.AGREE;

        }
        else if (annotationSelection.getAddressByUsername().containsKey(CURATION_USER)) {
            newState = AnnotationState.USE;

        }
        else {
            boolean doNotUse = false;
            for (AnnotationSelection otherAnnotationSelection : annotationSelection
                    .getAnnotationOption().getAnnotationSelections()) {
                if (otherAnnotationSelection.getAddressByUsername().containsKey(CURATION_USER)) {
                    doNotUse = true;
                    break;
                }
            }
            if (doNotUse) {
                newState = AnnotationState.DO_NOT_USE;
            }
            else {
                newState = AnnotationState.DISAGREE;

            }
        }
        return newState;
    }

    private static List<RelationType> getRelationTypes(GetDocumentResponse response,
            Map<Integer, AnnotationSelection> annotationSelectionByAddress, int numUsers,
            Relation relation, String arcTarget, List<AnnotationOption> aAnnotationOptions,
            Mode aMode, Entity aEntity)
    {
        int address = relation.getId();
        AnnotationSelection annotationSelection = annotationSelectionByAddress.get(address);
        AnnotationState newState = null;
        if (aMode.equals(Mode.CORRECTION)) {
            newState = getCorrectionState(annotationSelection, aAnnotationOptions, numUsers,
                    address);
        }
        else {
            newState = getCurationState(numUsers, annotationSelection);
        }
        if (newState != null) {
            String type = relation.getType() + "_(" + newState.name() + ")";
            String label = relation.getType().replace(AnnotationTypeConstant.DEP_PREFIX, "")
                    .replace(AnnotationTypeConstant.COREFERENCE_PREFIX, "");
            relation.setType(type);
            return getRelation(type, label, newState, Arrays.asList(new String[] { arcTarget }));
        }
        return new ArrayList<RelationType>();
    }

    public static Map<String, Object> getEntity(String type, String label,
            AnnotationState annotationState)
    {
        Map<String, Object> entityType = new HashMap<String, Object>();
        entityType.put("type", type);
        entityType.put("labels", new String[] { label });
        String color = annotationState.getColorCode();
        entityType.put("bgColor", color);
        entityType.put("borderColor", "darken");
        return entityType;
    }

    public static List<RelationType> getRelation(String type, String label,
            AnnotationState annotationState, List<String> arcTargets)
    {
        List<RelationType> arcs = new ArrayList<RelationType>();
        // if in AGREEMENT, make the arc color black in stead of light black
        if (annotationState.equals(AnnotationState.AGREE)) {
            annotationState = AnnotationState.AGREE_ARC;
        }
        RelationType arc = new RelationType(annotationState.getColorCode(), "triangle,5",
                Arrays.asList(label), type, arcTargets, "");
        arcs.add(arc);
        return arcs;
    }

    private static AnnotationState getCorrectionState(AnnotationSelection annotationSelection,
            List<AnnotationOption> aAnnotationOptions, int numUsers, int address)
    {
        AnnotationOption annotationOption = null;

        for (AnnotationOption annotationOption2 : aAnnotationOptions) {
            for (AnnotationSelection annotationSelection2 : annotationOption2
                    .getAnnotationSelections()) {
                if (annotationSelection2.getAddressByUsername().containsKey(CURATION_USER)
                        && annotationSelection2.getAddressByUsername().get(CURATION_USER) == address) {
                    annotationOption = annotationOption2;
                    break;
                }

            }
        }
        AnnotationState newState = null;
        if (annotationSelection == null) {
            newState = AnnotationState.NOT_SUPPORTED;

        }
        else if (annotationSelection.getAddressByUsername().size() == numUsers) {
            newState = AnnotationState.AGREE;

        }
        else if (annotationOption.getAnnotationSelections().size() == 1) {
            newState = AnnotationState.DISAGREE;
        }
        else {
            newState = AnnotationState.DO_NOT_USE;
        }
        return newState;
    }

    public static void updatePanel(
            AjaxRequestTarget aTarget,
            CurationSegmentPanel aParent,
            CurationContainer aCurationContainer,
            BratAnnotator aMergeVisualizer,
            RepositoryService aRepository,
            Map<String, Map<Integer, AnnotationSelection>> aAnnotationSelectionByUsernameAndAddress,
            CurationSegmentForSourceDocument aCurationSegment,
            AnnotationService aAnnotationService, MappingJacksonHttpMessageConverter aJsonConverter)
        throws UIMAException, ClassNotFoundException, IOException
    {
        SourceDocument sourceDocument = aCurationContainer.getBratAnnotatorModel().getDocument();
        Project project = aCurationContainer.getBratAnnotatorModel().getProject();
        List<AnnotationDocument> annotationDocuments = aRepository.listAnnotationDocuments(project,
                sourceDocument);
        Map<String, JCas> jCases = new HashMap<String, JCas>();
        JCas annotatorCas = null;
        // this is a CORRECTION project
        if (aCurationContainer.getBratAnnotatorModel().getMode().equals(Mode.CORRECTION)) {
            annotatorCas = aRepository.getCorrectionDocumentContent(sourceDocument);

            String username = SecurityContextHolder.getContext().getAuthentication().getName();

            User user = aRepository.getUser(username);
            AnnotationDocument annotationDocument = aRepository.getAnnotationDocument(
                    sourceDocument, user);
            jCases.put(user.getUsername(),
                    aRepository.getAnnotationDocumentContent(annotationDocument));
            aAnnotationSelectionByUsernameAndAddress.put(CURATION_USER,
                    new HashMap<Integer, AnnotationSelection>());
        }
        else {
            annotatorCas = aRepository.getCurationDocumentContent(sourceDocument);
            // get cases from repository
            BratCuratorUtility.getCases(jCases, annotationDocuments, aRepository,
                    aAnnotationSelectionByUsernameAndAddress);
        }
        // add mergeJCas separately
        jCases.put(CURATION_USER, annotatorCas);

        // get differing feature structures
        List<Type> entryTypes = CurationBuilder.getEntryTypes(annotatorCas,
                aCurationContainer.getBratAnnotatorModel().getAnnotationLayers());
        List<AnnotationOption> annotationOptions = null;
        try {
            annotationOptions = CasDiff.doDiff(entryTypes, jCases, aCurationSegment.getBegin(),
                    aCurationSegment.getEnd());
        }
        catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        // fill lookup variable for annotation selections
        BratCuratorUtility.fillLookupVariables(annotationOptions,
                aAnnotationSelectionByUsernameAndAddress,
                aCurationContainer.getBratAnnotatorModel());

        LinkedList<CurationUserSegmentForAnnotationDocument> sentences = new LinkedList<CurationUserSegmentForAnnotationDocument>();

        BratAnnotatorModel bratAnnotatorModel = null;
        if (!aCurationContainer.getBratAnnotatorModel().getMode().equals(Mode.CORRECTION)) {
            // update sentence address, offsets,... per sentence/per user in the curation view
            bratAnnotatorModel = BratCuratorUtility.setBratAnnotatorModel(sourceDocument,
                    aRepository, aCurationSegment, aAnnotationService);
        }
        else {
            bratAnnotatorModel = aCurationContainer.getBratAnnotatorModel();
        }

        BratCuratorUtility.populateCurationSentences(jCases, sentences, bratAnnotatorModel,
                annotationOptions, aAnnotationSelectionByUsernameAndAddress, aJsonConverter);
        // update sentence list on the right side
        aParent.setModelObject(sentences);
        if (aCurationContainer.getBratAnnotatorModel().getMode().equals(Mode.CURATION)) {
            aMergeVisualizer.setModelObject(bratAnnotatorModel);
            aMergeVisualizer.reloadContent(aTarget);
        }
        aTarget.add(aParent);

    }

    public static class NoOriginOrTargetAnnotationSelectedException
        extends BratAnnotationException
    {
        private static final long serialVersionUID = 1280015349963924638L;

        public NoOriginOrTargetAnnotationSelectedException(String message)
        {
            super(message);
        }

    }

}
