package eu.fbk.newsreader.naf;

import ixa.kaflib.Annotation;
import ixa.kaflib.CLink;
import ixa.kaflib.Coref;
import ixa.kaflib.Entity;
import ixa.kaflib.ExternalRef;
import ixa.kaflib.Factuality;
import ixa.kaflib.KAFDocument;
import ixa.kaflib.Predicate;
import ixa.kaflib.Span;
import ixa.kaflib.TLink;
import ixa.kaflib.Target;
import ixa.kaflib.Term;
import ixa.kaflib.Timex3;
import ixa.kaflib.WF;
import ixa.kaflib.Factuality.FactVal;
import ixa.kaflib.KAFDocument.Layer;
import ixa.kaflib.KAFDocument.LinguisticProcessor;
import ixa.kaflib.Predicate.Role;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;

import javax.xml.bind.JAXBException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class CATtoNAF {

	/**
	 * @param args
	 * @throws IOException 
	 * @throws SAXException 
	 * @throws ParserConfigurationException 
	 * @throws JAXBException 
	 * @throws InterruptedException 
	 * @throws TransformerException 
	 */

		
	public static void main(String[] args) throws JAXBException, ParserConfigurationException, SAXException, IOException, InterruptedException, TransformerException {
		//BufferedReader br = new BufferedReader(new FileReader(args[0]));
		
		//BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		String fileNameOut = args[1];
		//System.out.println(args[2]);
		CAT2NAF(new File(args[0]), new File(args[2]), fileNameOut);
		
		
	}
	
	public static String getTodayDate (){
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
		Date date = new Date();
		String dateString = dateFormat.format(date).toString();
		return dateString;
	}
	

	public static void CAT2NAF (File f, File fileNAF, String fileName) throws IOException, ParserConfigurationException, TransformerException{
		KAFDocument nafFile = KAFDocument.createFromFile(fileNAF);
		
		try{
			
			HashMap<String,String> convertCATidNAFid = new HashMap<String,String> ();
			HashMap<String,String> evInstIdType = new HashMap<String,String> ();
			HashMap<String,String> entInstIdType = new HashMap<String,String> ();
			HashMap<String,String> entInstIdDB = new HashMap<String,String> ();
			HashMap<String,Predicate> listPredNAF = new HashMap<String,Predicate> ();
			HashMap<String,Timex3> listTxNAF = new HashMap<String,Timex3> ();
			
			HashMap<String,Entity> listEntNAF = new HashMap<String,Entity> ();
			
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
			
			Document docCAT = docBuilder.parse(f);
			
			
			HashMap<String,Term> listTermsById = new HashMap<String,Term> ();
			for(Term t : nafFile.getTerms()){
				listTermsById.put(t.getId(),t);
			}
			HashMap<String,WF> listWFById = new HashMap<String,WF> ();
			for(WF wf : nafFile.getWFs()){
				listWFById.put(wf.getId(),wf);
			}
			
			//Add the linguistic processor description in the header of the NAF file
			String[] listLayer = new String[] {"entities","srl","timeExpressions","temporalRelations","causalRelations","coreferences","factualities"};
			for(String layerName : listLayer){
				LinguisticProcessor lp = nafFile.addLinguisticProcessor(layerName, "CAT");
				lp.setVersion("v0");
				lp.setBeginTimestamp(getTodayDate());
				lp.setEndTimestamp(getTodayDate());
			}
			
			//Convert EVENT_MENTION from CAT into predicate in NAF
			NodeList events = docCAT.getElementsByTagName("EVENT_MENTION");
			int idEv = 1;
			for (int i=0; i<events.getLength(); i++){
				Node ev = events.item(i);

				List<Term> eventTerms = new ArrayList<Term>();
				
				NodeList tokAnch = ev.getChildNodes();
				int nbTok = 0;
				int idPrevTok = 0;
				boolean conj = false;
				for(int j=0; j<tokAnch.getLength(); j++){
					if(tokAnch.item(j).getNodeName().equals("token_anchor")){
						if(nbTok > 0 && idPrevTok < Integer.parseInt(tokAnch.item(j).getAttributes().getNamedItem("t_id").getNodeValue()) -1){
							conj = true;
							break;
						}
						else{
							eventTerms.add(listTermsById.get("t"+tokAnch.item(j).getAttributes().getNamedItem("t_id").getNodeValue()));	
							idPrevTok = Integer.parseInt(tokAnch.item(j).getAttributes().getNamedItem("t_id").getNodeValue());
							nbTok++;
						}
					}
				}
				
				//Add factuality element for each event mention
				if(!conj){
	                Span s = nafFile.newSpan(eventTerms);
	                String eventId = "pr"+Integer.toString(idEv);
	                Predicate pr = nafFile.newPredicate(eventId,s);
	                convertCATidNAFid.put(ev.getAttributes().getNamedItem("m_id").getNodeValue(), "pr"+Integer.toString(idEv));
	                listPredNAF.put(pr.getId(),pr);
	                
	                if(ev.getAttributes().getNamedItem("certainty") != null && !ev.getAttributes().getNamedItem("certainty").getNodeValue().equals("")){
                		
	                	Factuality fact = nafFile.newFactuality(s);
                    	String polarity = ev.getAttributes().getNamedItem("polarity").getNodeValue();
                    	String certainty = ev.getAttributes().getNamedItem("certainty").getNodeValue();
                    	String eventTime = ev.getAttributes().getNamedItem("time").getNodeValue();
                    	
                    	FactVal factval = nafFile.newFactVal(polarity, "nwr:attributionPolarity");
                        fact.addFactVal(factval);
                    	
                        factval = nafFile.newFactVal(certainty, "nwr:attributionCertainty");
                        fact.addFactVal(factval);
                        
                        factval = nafFile.newFactVal(eventTime, "nwr:attributionTense");
                        fact.addFactVal(factval);
                    }
	                
	                idEv++;
				}
			}
			
			//Convert ENTITY_MENTION from CAT into entity elements in NAF
			NodeList entities = docCAT.getElementsByTagName("ENTITY_MENTION");
			for (int i=0; i<entities.getLength(); i++){
				Node ev = entities.item(i);
				
				List<Term> entityTerms = new ArrayList<Term>();
				
				NodeList tokAnch = ev.getChildNodes();
				for(int j=0; j<tokAnch.getLength(); j++){
					if(tokAnch.item(j).getNodeName().equals("token_anchor")){
						entityTerms.add(listTermsById.get("t"+tokAnch.item(j).getAttributes().getNamedItem("t_id").getNodeValue()));
					}
				}
				
				if(ev.getAttributes().getNamedItem("syntactic_type") != null && ev.getAttributes().getNamedItem("syntactic_type").getNodeValue().matches("((NAM)|(PRE.NAM))")){
					List eLink = new ArrayList<List>();
	                eLink.add(entityTerms);
	                Entity ent = nafFile.createEntity("NIL", eLink);
	                
	                convertCATidNAFid.put(ev.getAttributes().getNamedItem("m_id").getNodeValue(), ent.getId());
				}
        
			}
			
			//Convert VALUE from CAT into entity elements in NAF with type "NUM"
			NodeList numExpressions = docCAT.getElementsByTagName("VALUE");
			for (int i=0; i<numExpressions.getLength(); i++){
				Node numEx = numExpressions.item(i);
				
				List<Term> entityTerms = new ArrayList<Term>();
				
				NodeList tokAnch = numEx.getChildNodes();
				for(int j=0; j<tokAnch.getLength(); j++){
					if(tokAnch.item(j).getNodeName().equals("token_anchor")){
						entityTerms.add(listTermsById.get("t"+tokAnch.item(j).getAttributes().getNamedItem("t_id").getNodeValue()));
					}
				}
				
				List eLink = new ArrayList<List>();
                eLink.add(entityTerms);
                Entity ent = nafFile.createEntity("NUM", eLink);
                
                convertCATidNAFid.put(numEx.getAttributes().getNamedItem("m_id").getNodeValue(), ent.getId());
        
			}
			
			
			//Add dbpedia link and entity type from ENTITY instances in CAT into entity elements in NAF
			NodeList entityInst = docCAT.getElementsByTagName("ENTITY");
			for (int i=0; i<entityInst.getLength(); i++){
				Node ev = entityInst.item(i);
				entInstIdType.put(ev.getAttributes().getNamedItem("m_id").getNodeValue(), ev.getAttributes().getNamedItem("ent_type").getNodeValue());
			
				//DBpedia link
				if(!ev.getAttributes().getNamedItem("external_ref").getNodeValue().equals("")){
					entInstIdDB.put(ev.getAttributes().getNamedItem("m_id").getNodeValue(),ev.getAttributes().getNamedItem("external_ref").getNodeValue());
				}
				
			}
			
			//Convert TIMEX3 from CAT into timex3 elements in NAF
			NodeList timexes = docCAT.getElementsByTagName("TIMEX3");
			int tmxid = 1;
			for (int i=0; i<timexes.getLength(); i++){
				Node tmx = timexes.item(i);
				
				Timex3 tmxNaf = nafFile.newTimex3("tmx"+Integer.toString(tmxid),tmx.getAttributes().getNamedItem("type").getNodeValue());
	            tmxNaf.setValue(tmx.getAttributes().getNamedItem("value").getNodeValue());
	            Span s = nafFile.newSpan();
	             
	            NodeList tokAnch = tmx.getChildNodes();
				for(int j=0; j<tokAnch.getLength(); j++){
					if(tokAnch.item(j).getNodeName().equals("token_anchor")){
						s.addTarget(listWFById.get("w"+tokAnch.item(j).getAttributes().getNamedItem("t_id").getNodeValue()));
					}
				}

	            tmxNaf.setSpan(s);
	             
                convertCATidNAFid.put(tmx.getAttributes().getNamedItem("m_id").getNodeValue(), "tmx"+Integer.toString(tmxid));
        
                tmxid ++;
                
                if(tmx.getAttributes().getNamedItem("functionInDocument") != null && tmx.getAttributes().getNamedItem("functionInDocument").getNodeValue().equals("CREATION_TIME")){
                	Timex3 tmxDCT = nafFile.newTimex3("tmx0",tmx.getAttributes().getNamedItem("type").getNodeValue());
    	            tmxDCT.setValue(tmx.getAttributes().getNamedItem("value").getNodeValue());
    	            tmxDCT.setFunctionInDocument("CREATION_TIME");
                }
			}
			
			HashMap<String,Entity> listEntityNaf = new HashMap<String,Entity> ();
			for (Entity ent : nafFile.getEntities()){
				listEntityNaf.put(ent.getId(),ent);
			}
			
			
			//Convert REFERS_TO relations from CAT into coreference elements in NAF (for events and entities)
			NodeList refersto = docCAT.getElementsByTagName("REFERS_TO");
			int corefid = 1;
			for(int i=0; i<refersto.getLength(); i++){
				
				Node rel = refersto.item(i);
				NodeList srctar = rel.getChildNodes();
				String enttype = "";
				List<String> sources = new ArrayList<String> ();
				
				Boolean entity = false;
				for (int k=0; k<srctar.getLength(); k++){
					if(srctar.item(k).getNodeName().equals("target")){
						if(entInstIdType.containsKey(srctar.item(k).getAttributes().getNamedItem("m_id").getNodeValue())){
							enttype = entInstIdType.get(srctar.item(k).getAttributes().getNamedItem("m_id").getNodeValue());
							entity = true;
						}
					}
					else if(srctar.item(k).getNodeName().equals("source")){
						sources.add(srctar.item(k).getAttributes().getNamedItem("m_id").getNodeValue());
					}
				}
				
				List<List<Target>> listSpanCoref = new ArrayList<List<Target>> ();
				
				
				for(String src : sources){
					if(convertCATidNAFid.containsKey(src) && listEntityNaf.containsKey(convertCATidNAFid.get(src))){
						
						listEntityNaf.get(convertCATidNAFid.get(src)).setType(enttype);
						
						List<Span<Term>> sourceSpan = listEntityNaf.get(convertCATidNAFid.get(src)).getSpans();
						List<Target> sourceTar = new ArrayList<Target> ();
						for (Span s : sourceSpan){
							for (int k=0; k<s.getTargets().size(); k++){
								Target tt = nafFile.createTarget((Term) s.getTargets().get(k));
								sourceTar.add(tt);
							}
						}
						listSpanCoref.add(sourceTar);
					
						if(convertCATidNAFid.containsKey(src) && entInstIdDB.containsKey(convertCATidNAFid.get(src))){
							ExternalRef exref = nafFile.createExternalRef("dbp", entInstIdDB.get(convertCATidNAFid.get(src)));
							listEntityNaf.get(convertCATidNAFid.get(src)).addExternalRef(exref);
						}
					}
					else{
						if(convertCATidNAFid.containsKey(src)){
							Span<Term> sourceSpan = listPredNAF.get(convertCATidNAFid.get(src)).getSpan();
							List<Target> sourceTar = new ArrayList<Target> ();
							for (Term t : sourceSpan.getTargets()){
								Target tt = nafFile.createTarget(t);
								sourceTar.add(tt);
							}
							
							listSpanCoref.add(sourceTar);
						}
					}
					
				}
				
				if(listSpanCoref.size() > 0){
					String pref = "co";
					if(!entity){
						pref = "coevent";
					}
					Coref coref = nafFile.createCoref(pref+Integer.toString(corefid), listSpanCoref);
					
					if(!entity){
						coref.setType("event");
					}
					corefid ++;
				}
			}
			
			//Convert HAS_PARTICIPANT relations from CAT into semantic roles in NAF
			NodeList haspart = docCAT.getElementsByTagName("HAS_PARTICIPANT");
			for(int i=0; i<haspart.getLength(); i++){
				Node rel = haspart.item(i);
				NodeList srctar = rel.getChildNodes();
				
				String targetId = "";
				String sourceId = "";
				
				
				for (int k=0; k<srctar.getLength(); k++){
					if(srctar.item(k).getNodeName().equals("target")){
						targetId = srctar.item(k).getAttributes().getNamedItem("m_id").getNodeValue();
					}
					else if(srctar.item(k).getNodeName().equals("source")){
						sourceId = srctar.item(k).getAttributes().getNamedItem("m_id").getNodeValue();
					}
				}
				if(listPredNAF.containsKey(convertCATidNAFid.get(sourceId))){
					Predicate pr = listPredNAF.get(convertCATidNAFid.get(sourceId));
					if(convertCATidNAFid.containsKey(targetId)){
						Span spanMod = nafFile.newSpan();
						for(Term t : listEntityNaf.get(convertCATidNAFid.get(targetId)).getSpans().get(0).getTargets()){
							spanMod.addTarget(t);
						}
						Role r = nafFile.newRole(pr, rel.getAttributes().getNamedItem("sem_role").getNodeValue(), spanMod);
						pr.addRole(r);
					}
				}
				
			}
			
			for(Timex3 tx : nafFile.getTimeExs()){
				listTxNAF.put(tx.getId(), tx);
			}
			
			//Convert TLINK relations from CAT into tlink elements in NAF
			NodeList tlink = docCAT.getElementsByTagName("TLINK");
			for(int i=0; i<tlink.getLength(); i++){
				Node rel = tlink.item(i);
				NodeList srctar = rel.getChildNodes();
			
				String targetId = "";
				String sourceId = "";
				
				for (int k=0; k<srctar.getLength(); k++){
					if(srctar.item(k).getNodeName().equals("target")){
						targetId = srctar.item(k).getAttributes().getNamedItem("m_id").getNodeValue();
					}
					else if(srctar.item(k).getNodeName().equals("source")){
						sourceId = srctar.item(k).getAttributes().getNamedItem("m_id").getNodeValue();
					}
				}
				
				
				if (convertCATidNAFid.containsKey(targetId) && convertCATidNAFid.containsKey(sourceId) && rel.getAttributes().getNamedItem("reltype") != null){
					
					if(listPredNAF.containsKey(convertCATidNAFid.get(sourceId))){
						Predicate pr = listPredNAF.get(convertCATidNAFid.get(sourceId));
						//event-event
						if(listPredNAF.containsKey(convertCATidNAFid.get(targetId))){
							Predicate prt = listPredNAF.get(convertCATidNAFid.get(targetId));
							
							nafFile.newTLink(pr, prt, rel.getAttributes().getNamedItem("reltype").getNodeValue());
						}
						//event-timex
						else if(listTxNAF.containsKey(convertCATidNAFid.get(targetId))){
							Timex3 tx = listTxNAF.get(convertCATidNAFid.get(targetId));
							
							nafFile.newTLink(pr, tx, rel.getAttributes().getNamedItem("reltype").getNodeValue());
						}
					}
					else if(listTxNAF.containsKey(convertCATidNAFid.get(sourceId))){
						Timex3 txSrc = listTxNAF.get(convertCATidNAFid.get(sourceId));
						//timex-event
						if(listPredNAF.containsKey(convertCATidNAFid.get(targetId))){
							Predicate prt = listPredNAF.get(convertCATidNAFid.get(targetId));
							
							nafFile.newTLink(txSrc, prt, rel.getAttributes().getNamedItem("reltype").getNodeValue());
						}
						//timex-timex
						else if(listTxNAF.containsKey(convertCATidNAFid.get(targetId))){
							Timex3 tx = listTxNAF.get(convertCATidNAFid.get(targetId));
							
							nafFile.newTLink(txSrc, tx, rel.getAttributes().getNamedItem("reltype").getNodeValue());
						}
					}
				
				}
			}
				
			//Convert CLINK relations from CAT into clink elements in NAF
			NodeList clink = docCAT.getElementsByTagName("CLINK");
			for(int i=0; i<clink.getLength(); i++){
				Node rel = clink.item(i);
				NodeList srctar = rel.getChildNodes();
				
				String targetId = "";
				String sourceId = "";
				
				for (int k=0; k<srctar.getLength(); k++){
					if(srctar.item(k).getNodeName().equals("target")){
						targetId = srctar.item(k).getAttributes().getNamedItem("m_id").getNodeValue();
					}
					else if(srctar.item(k).getNodeName().equals("source")){
						sourceId = srctar.item(k).getAttributes().getNamedItem("m_id").getNodeValue();
					}
				}
					
				if(convertCATidNAFid.containsKey(sourceId) && convertCATidNAFid.containsKey(targetId) &&
						listPredNAF.containsKey(convertCATidNAFid.get(sourceId)) && listPredNAF.containsKey(convertCATidNAFid.get(targetId))){
					Predicate prSrc = listPredNAF.get(convertCATidNAFid.get(sourceId));
					Predicate prTar = listPredNAF.get(convertCATidNAFid.get(targetId));
					
					nafFile.newCLink(prSrc, prTar);
				}
			}
			
			
		} catch (Exception e){
			System.out.println(e.toString());
		}
		

		//System.out.println("print");
		//nafFile.print();
		nafFile.save(fileName);
		
	}
	

	/**
	 * Get a Coreferences from a term id (wid)
	 * @param wid
	 * @param nafFile
	 * @return Predicate or null
	 */
	private static Coref getCoreferences (String wid, KAFDocument nafFile) {
		ListIterator<Coref> corefl = nafFile.getCorefs().listIterator();
		while (corefl.hasNext()) {
			Coref co = corefl.next();
			if ((co.getType() != null && co.getType().equals("event")) || (co.getId().startsWith("coevent"))){
				ListIterator<Term> tarl = co.getTerms().listIterator();
				while (tarl.hasNext()) {
					Term tar = tarl.next();
					if (tar.getId().equals(wid)) {
						return co;
					}
				}
			}
		}
		return null;
	}
	
	/**
	 * Get the event type of a predicate
	 * @param pred
	 * @return
	 */
	private static String getEventType (Predicate pred){
		String eventType = "";
		ListIterator<ExternalRef> exRefl = pred.getExternalRefs().listIterator();
		while(exRefl.hasNext()){
			ExternalRef exRef = exRefl.next();
			if(exRef.getResource().equals("EventType")){
				eventType = exRef.getReference();
				//contextual, communication, cognition, grammatical
				if (eventType.equals("contextual")) eventType = "OTHER";
				else if(eventType.equals("communication") || eventType.equals("cognition")) eventType = "SPEECH_COGNITIVE";
				else if (eventType.equals("grammatical")) eventType = "GRAMMATICAL";
				else eventType = "OTHER";
			}
			else eventType = "OTHER";
		}
		return eventType;
	}

	/**
	 * Get a Predicate from a word id (wid)
	 * @param wid
	 * @param nafFile
	 * @return Predicate or null
	 */
	private static Predicate getPredicate (String wid, KAFDocument nafFile, int sent) {
		//ListIterator<Predicate> predl = nafFile.getPredicates().listIterator();
		//ListIterator<Predicate> predl = nafFile.getPredicatesBySent(sent).listIterator();
		List<Predicate> pl = nafFile.getPredicatesBySent(sent);
		if(pl != null){
			ListIterator<Predicate> predl = pl.listIterator();
			while (predl.hasNext()) {
				Predicate pred = predl.next();
				ListIterator<Term> tarl = pred.getTerms().listIterator();
				
				while (tarl.hasNext()) {
					Term tar = tarl.next();
					if (tar.getId().equals(wid)) {
						return pred;
					}
				}
			}
		}
		return null;
	}
	
	
	/**
	 * Get the DCT object
	 * @param nafFile
	 * @return Timex3 or null
	 */
	private static Timex3 getDCT (KAFDocument nafFile) {
		ListIterator<Timex3> txl = nafFile.getTimeExs().listIterator();
		while (txl.hasNext()) {
			Timex3 tx3= txl.next();
			if(tx3.getFunctionInDocument() != null && tx3.getFunctionInDocument().equals("CREATION_TIME")){
				return tx3;
			}
		}
		return null;
	}
}
