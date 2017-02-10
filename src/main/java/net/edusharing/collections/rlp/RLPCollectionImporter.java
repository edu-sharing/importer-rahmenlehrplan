package net.edusharing.collections.rlp;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;

import com.google.gson.Gson;

import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;

import net.edusharing.collections.rlp.xmlclasses.Competenceareatype;
import net.edusharing.collections.rlp.xmlclasses.Competencetype;
import net.edusharing.collections.rlp.xmlclasses.Fachtype;
import net.edusharing.collections.rlp.xmlclasses.Standardtype;
import net.edusharing.collections.rlp.xmlclasses.Stufentype;
import net.edusharing.collections.rlp.xmlclasses.Themainhalttype;


@SuppressWarnings("deprecation")
public class RLPCollectionImporter {	
		
	/*
	 * Configuration
	 */
	
	// the URL of the edu-sharing repository where to import the data (use HTTPS for production)
	// for example if you have edu-sharing running local on port 8080
	// the URL would be http://localhost:8080/edu-sharing/
	final static String eduSharingApiURL = "http://localhost:8080/edu-sharing/";
	
	// the account data
	final static String username = "admin";
	final static String password = "admin";
	
	// the node id of the root collection under which the other collections should get created
	// for example a node id looks like this: 116246a6-66b6-4853-b671-2f0af3f9f6aa
	// you can find the 'id' of the collection node in the URL when browsing edu-sharing
	final static String lehrplanCollectionId = "";	
	
	// set the file from which the extra information should be loaded
	final static String nameOfExtraDataFile = "berlinbrandenburg.extra.json";
	
	/*
	 * Main Script
	 */
		
	public static void main(String[] args) throws Exception {
		
		// load extra data
		extraDataMap = loadExtraDataFromFile(nameOfExtraDataFile);
		
		// test oAuth / account data
		if (getAuthToken()==null) {
			System.err.println("EXIT - not able to get valid oAuth Token - have you set the varibales 'eduSharingApiURL','username' and 'password' correctly?");
			System.exit(0);
		}
				
		// the file path where the XML data of the RLP is
		final String basePathDataXML = "src/main/java/net/edusharing/collections/rlp/xmldata/";
		
		/*
		 * load xml data
		 * Get all the single "fach" XML files from directory
		 */
        
        // check if folder exists and is valid
        File xmlFolder = new File(basePathDataXML);
        if ((!xmlFolder.exists())) {
        	System.err.println("FAIL - The path '"+basePathDataXML+"' to find XML data does not exist. EXIT");
        	System.exit(-1);
        }
        if (!xmlFolder.isDirectory()) {
        	System.err.println("FAIL - The path '"+basePathDataXML+"' to find XML is no directory. EXIT");
        	System.exit(-1);	
        }
        
        // get all XML files from folder
        System.out.println("**** Chech Data Directory *****");
        File[] directoryFiles = xmlFolder.listFiles();
        Vector<File> xmlFiles = new Vector<File>();
        for (int i=0; i<directoryFiles.length; i++) {
        	File file = directoryFiles[i];
        	System.out.print("Found File: "+file.getName());
        	if (file.getName().toLowerCase().endsWith(".xml")) {
        		xmlFiles.add(file);
            	System.out.println(" YES XML --> MARK FOR PROCESSING");		
        	} else {
            	System.out.println(" NO XML --> IGNORE");	
        	}
        }
        
        // check if any results
        if (xmlFiles.size()==0) {
        	System.err.println("FAIL - In path '"+basePathDataXML+"' no XML files found. EXIT");
        	System.exit(-1);	
        }
        
        // check which XML files are from type "Fach"
        System.out.println("**** XML Conversion *****");
	    JAXBContext jc = JAXBContext.newInstance(Fachtype.class);
        Unmarshaller unmarshaller = jc.createUnmarshaller();
        Vector<Fachtype> faecher = new Vector<Fachtype>();
        for (File file : xmlFiles) {
			try {
		        Fachtype fach = (Fachtype) unmarshaller.unmarshal(new File(basePathDataXML+file.getName()));
		        
		        // make sure first letter of fach title is upper case
		        char[] stringArray = fach.getTitle().trim().toCharArray();
		        stringArray[0] = Character.toUpperCase(stringArray[0]);
		        fach.setTitle(new String(stringArray));
		        
		        if ((fach.getTitle()==null) || (fach.getTitle().trim().length()==0)) fach.setTitle(fach.getId());
            	System.out.println("OK '"+file.getName()+"' --> Found XML for Fach '"+fach.getTitle()+"'");
            	faecher.add(fach);
			} catch (Exception e) {
            	System.out.println("FAIL '"+file.getName()+"' --> NOT A FACH XML");
			}
		}
        
        // check if any results
        if (faecher.size()==0) {
        	System.err.println("FAIL - In path '"+basePathDataXML+"' no XML are from type Fach. EXIT");
        	System.exit(-1);	
        }
        
        
        /*
         * tranverse curriculum and create collections with sub collections  	
         */

        for (Fachtype fach : faecher) {
        	
        	String fachCollectionRef = createCollection(lehrplanCollectionId, fach.getTitle(), fach.getId(), "Fach");
			if (fachCollectionRef==null) continue;
        	
        	for (Competenceareatype area : fach.getC2().getArea()) {
        		
        		String areaCollectionRef = createCollection(fachCollectionRef, area.getName(), area.getId(), "Kompetenzbereich");
				if (areaCollectionRef==null) continue;
        		
        		// create direct competences if available
        		processCompetences(areaCollectionRef, area.getCompetence());
        		
        		// go into sub areas if available
            	if (area.getSubarea()!=null) for (Competenceareatype subarea : area.getSubarea()) {
            		
            		String subAreaCollectionRef = createCollection(areaCollectionRef, subarea.getName(), subarea.getId(), "Unterkompetenzbereich");
    				if (subAreaCollectionRef==null) continue;
            		
            		// create sub area competences if available
            		processCompetences(subAreaCollectionRef, subarea.getCompetence());
           
            	}
            		
            }
        	
        	// also take the 
        	for (Themainhalttype inhalt : fach.getC3().getThemainhalt()) {
        		
        		processThemainhalt(fachCollectionRef, inhalt, 0);
        		
        	}
        	
		}	
        
        System.out.println("---> DONE "+collectionsCreated+" collections created <----");

	}
	
	private static void processThemainhalt(String parentRef, Themainhalttype inhalt, int recursiveLevel) {
		
		if (recursiveLevel>10) {
			System.err.println("TOO MUCH Recursion - SAFTY EXIT");
			System.exit(0);
		}
		
		// concatenate all contents to description
		String desc = "";
		for (String cont : inhalt.getContent()) desc += (" " + cont);
		desc = desc.trim();
		
		String newCollectionRef = createCollection(parentRef, inhalt.getTitle(), inhalt.getId(), desc);
		
		for (Themainhalttype subInhalt : inhalt.getInhalt()) {
			processThemainhalt(newCollectionRef, subInhalt, recursiveLevel+1);
		}
		
	}
		
	private static void processCompetences(String parent, List<Competencetype> list) {
		
		if ((list!=null) && (list.size()>0)) {
			
			for (Competencetype competence : list) {
				
				String competenceCollectionRef =  createCollection(parent, competence.getName(), competence.getId(), "Kompetenz");
				if (competenceCollectionRef==null) continue;
				
				for (Stufentype stufe : competence.getStufe()) {
					
					String stufenDescription = getDescription(stufe.getLevel());
					String stufenCollectionRef = createCollection(competenceCollectionRef, stufe.getLevel(), stufe.getId(), stufenDescription);
					if (stufenCollectionRef==null) continue;
					
					int number = 0;
					for (Standardtype standard : stufe.getStandard()) {
						
						number++;
						if (createCollection(stufenCollectionRef, "Standard "+number, standard.getId(), standard.getContent()) == null) continue;
						
					}
					
				}
				
			}
			
		}
	
	}
	
	/*
     * preset description texts for the different levels
     */
    private static String getDescription (String level){
    	String descr = "";
    	if (level.contains("A")){ 
    		descr += "Die Niveaustufe A orientiert sich an einem Niveau, das Schülerinnen und Schüler im Allgemeinen am Ende der Jahrgangsstufe 1 erreichen. ";
    	}
    	if (level.contains("B")){ 
    		descr += "Die Niveaustufe B orientiert sich an einem Niveau, das Schülerinnen und Schüler im Allgemeinen am Ende der Schuleingansphase erreichen. ";
    	}
    	if (level.contains("C")){ 
    		descr += "Die Niveaustufe C orientiert sich an einem Niveau, das Schülerinnen und Schüler im Allgemeinen am Ende der Jahrgangsstufe 4 erreichen. ";
    	}
    	if (level.contains("D")){ 
    		descr += "Die Niveaustufe D orientiert sich an einem Niveau, das Schülerinnen und Schüler im Allgemeinen am Ende der Jahrgangsstufe 6 erreichen. ";
    	}
    	if (level.contains("E")){ 
    		descr += "Die Niveaustufe E orientiert sich an einem Niveau, das Schülerinnen und Schüler im Allgemeinen am Ende der Jahrgangsstufe 8 erreichen. ";
    	}
    	if (level.contains("F")){ 
    		descr += "Die Niveaustufe F orientiert sich an einem Niveau, das Schülerinnen und Schüler im Allgemeinen am Ende der Jahrgangsstufe 9 erreichen. (Entsprechend KMK-Standards für Hauptschulabschluss) ";
    	}
    	if (level.contains("G")){ 
    		descr += "Die Niveaustufe G orientiert sich an einem Niveau, das Schülerinnen und Schüler im Allgemeinen am Ende der Jahrgangsstufe 10 erreichen. (Entsprechend KMK-Standards für den MSA) ";
    	}
    	if (level.contains("H")){ 
    		descr += "Die Niveaustufe H orientiert sich an einem Niveau orientieren, das Schülerinnen und Schüler am Gymnasium im Allgemeinen am Ende der Jahrgangsstufe 10 erreichen. (orientiert sich an den Eingangsvoraussetzungen der Rahmenlehrpläne für die gymnasiale Oberstufe) ";
    	}
    	return descr;   	
    }
        
    /*
     * API CALLS
     * calling the edu-sharing API
     */
    
    static int collectionsCreated = 0;
    
    // create collection
	private static String createCollection(String nodeId, String name, String id, String desc) {

		String newCollectionId = null;
		
		// if a name is not set - dont make an extra collection
		if ((name==null) || (name.trim().length()==0)) return nodeId;
				
		// create full description
		String description = "";
		if (id!=null) description += "("+id+") ";
		if (desc!=null) description += desc;
	
		// clean uop name
		name = name.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");
		name = name.replace(':', ' ').replace('"', '\'');
		name = name.replace((char)10, ' ').replace((char)13, ' ').replace((char)9, ' ').replace("  ", " ").trim();

		// clean up description
		description = description.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "");
		description = description.replace('"', '\'');
		description = description.replace((char)10, ' ').replace((char)13, ' ').replace((char)9, ' ').replace("  ", " ").trim();
	
		// check if there is extra data for this collection
		File imageFile = null;
		String color = "#759CB7"; // default
		if (extraDataMap.containsKey(id.toUpperCase())) {
			ExtraData extraData = extraDataMap.get(id.toUpperCase());
			
			// set color
			if (extraData.color!=null) color = extraData.color;
			
			// set file image
			if (extraData.image!=null) {
				imageFile = new File("./collectionData/"+extraData.image);
				if (!imageFile.exists()) {
					System.err.println("WAS NOT ABLE TO FIND EXTRA DATA IMAGE '"+extraData.image+"' --> ("+imageFile.getAbsolutePath()+")");
					System.out.println("\007");
					System.out.flush();
					try { Thread.sleep(10000); } catch (Exception e) {}
					imageFile = null;
				}
			}
		}	
		
		// try to create collection
		try{
			
			// courtesy delay to not overload the API
			try {
				Thread.sleep(250);
			} catch (Exception e) {}
			
			System.out.println("");
			System.out.println("**** create collection on parent nodeId("+nodeId+") *******");
			
			String jsonStr = "{ \"title\":\""+name+"\", \"description\":\""+description+"\",\"color\":\""+color+"\",\"type\":\"default\", \"scope\":\"EDU_ALL\" }";
	    	System.out.println(jsonStr);
		
	    	String url = eduSharingApiURL+"rest/collection/v1/collections/-home-/"+nodeId+"/children";
	    	System.out.println("URL --> "+url);
	    	
	    	CloseableHttpClient httpclient = HttpClients.createDefault();
	    	HttpPost httpPost = new HttpPost(url);
	    	httpPost.addHeader("Content-Type", "application/json; charset=utf-8");
	    	httpPost.addHeader("Authorization","Bearer " + getAuthToken());
	    	httpPost.addHeader("Accept", "application/json");
	    	httpPost.setEntity(new StringEntity(jsonStr,  HTTP.UTF_8));
	    	CloseableHttpResponse response1 = httpclient.execute(httpPost);
			
	    	try {
	    		
	    		// get data from HTTP request
	    	    System.out.println("HTTP response --> "+response1.getStatusLine());
	    	    HttpEntity entity1 = response1.getEntity();
	    	    String data = convertStreamToString(entity1.getContent());
	    	    EntityUtils.consume(entity1);
	    	    
	    	    // check if HTTP API worked
	    	    if (!response1.getStatusLine().toString().startsWith("HTTP/1.1 200")) {
	    	    	System.err.println("HTTP REQUEST FAILED: "+response1.getStatusLine()+" / "+data);
	    	    	if (response1.getStatusLine().toString().startsWith("HTTP/1.1 404")) {
		    	    	System.err.println("Have you set the variable 'lehrplanCollectionId' correctly?");
		    	    	System.exit(0);
	    	    	} else {
	    	    		throw new Exception("HTTP REQUEST FAILED: "+response1.getStatusLine()+" / "+data);
	    	    	}
	    	    }
	    	    
	    	    // get collection id from response
		    	int start = data.indexOf("id\":\"") + 5;
		    	if (start<=5) throw new Exception("ID BEGIN not found in response: "+data);
		    	int end = data.indexOf('"',start+1);
		    	if (end<=0) throw new Exception("ID END not found in response: "+data);;
		    	newCollectionId = data.substring(start, end);	    
		    	System.out.println("OK new CollectionID --> "+newCollectionId);
		    	collectionsCreated++;
		    	   	    	    
		    	// add image to collection if set
		    	if (imageFile!=null) {
		    		addImageToCollection(newCollectionId, imageFile);
		    	}
	    	    
	    	} finally {
	    	    response1.close();
	    	}   	
	    	
		}catch(Exception e){
			e.printStackTrace();
			return null;
		}
	
		
		return newCollectionId;
		
	}
	
	// upload an image as icon to a collection
	private static boolean addImageToCollection(String collectionId, File imageFile) {
		
		boolean result = false;
		
		try{
		
		System.out.println("**** upload image *******************************");
		
    	String url = eduSharingApiURL+"rest/collection/v1/collections/-home-/"+collectionId+"/icon?mimetype=image%2Fpng";
    	CloseableHttpClient httpclient = HttpClients.createDefault();
    	HttpPost httpPost = new HttpPost(url);
    	httpPost.addHeader("Authorization","Bearer " + getAuthToken());
    	httpPost.addHeader("Accept", "application/json");
    
		MultipartEntity reqEntity = new MultipartEntity();
    	reqEntity.addPart("filename", new StringBody(""));
    	reqEntity.addPart("file", new FileBody(imageFile));

    	httpPost.setEntity(reqEntity);
    	CloseableHttpResponse response1 = httpclient.execute(httpPost);
		
    	try {
    	    System.out.println(response1.getStatusLine());
    	    result = true;
    	    HttpEntity entity1 = response1.getEntity();
    	    String data = convertStreamToString(entity1.getContent());
    	    System.out.println(data);
    	    EntityUtils.consume(entity1);
    	} finally {
    	    response1.close();
    	}   	
    	
	} catch(Exception e){
		e.printStackTrace();
	}

		return result;
	}
	
    static String convertStreamToString(java.io.InputStream is) throws Exception {
        @SuppressWarnings("resource")
		java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }    
	
	/*
	 * OAUTH - Quick Hack
	 * every 1 minute make fresh oAuth login and get token 
	 */ 
	
	static String oAuthToken = null;
	static Long   oAuthLastRefresh = 0l;
    
    private static String getAuthToken() {
    	
    	// if last login is still valid
    	long milliSecsSinceLast = System.currentTimeMillis()-oAuthLastRefresh;
    	System.out.println("milliSecsSinceLast oAuth("+milliSecsSinceLast+")");
    	if ( (oAuthToken!=null) && (milliSecsSinceLast<60000l) ) {
    		return oAuthToken;
    	}
    	
    	// get fresh oAuth token (ignore refresh token)
    	try {
    		
    		String url = eduSharingApiURL+"oauth2/token";
    		System.out.println("**** GET fresh OAUTH TOKEN from "+url);
    		CloseableHttpClient httpclient = HttpClients.createDefault();
    		HttpPost httpPost = new HttpPost(url);
    		httpPost.addHeader("Content-Type", "application/x-www-form-urlencoded");
    		httpPost.addHeader("Accept", "*/*");
    		List <NameValuePair> nvps = new ArrayList <NameValuePair>();
    		nvps.add(new BasicNameValuePair("grant_type", "password"));
    		nvps.add(new BasicNameValuePair("client_id", "eduApp"));
    		nvps.add(new BasicNameValuePair("client_secret", "secret"));
    		nvps.add(new BasicNameValuePair("username", username));
    		nvps.add(new BasicNameValuePair("password", password));
    		httpPost.setEntity(new UrlEncodedFormEntity(nvps));
    		CloseableHttpResponse response1 = httpclient.execute(httpPost);
    		try {
    			System.out.println(response1.getStatusLine());
    			HttpEntity entity1 = response1.getEntity();
    			String data = convertStreamToString(entity1.getContent());
    			System.out.println(data);
    			int start = data.indexOf("access_token\":\"") + 15;
    			if (start<=0) return null;
    			int end = data.indexOf('"',start+1);
    			if (end<=0) return null;
    			oAuthToken = data.substring(start, end);
    			oAuthLastRefresh = System.currentTimeMillis();
    			System.out.println("*** GOT FRESH oAuth TOKEN ----> "+oAuthToken);
    			EntityUtils.consume(entity1);
    		} finally {
    			response1.close();
    		}
    	
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    	
    	return oAuthToken;
    }  
    
    public static boolean updatePropertyOnCollection(String nodeId, String propKey, String propValue) {
    	
    	// *** 1. Get Collection from API ***
    	
    	// get fresh oAuth token (ignore refresh token)
    	try {
    		
    		String url = eduSharingApiURL+"rest/collection/v1/collections/-home-/"+nodeId;
    		System.out.println("**** GET fresh OAUTH TOKEN from "+url);
    		CloseableHttpClient httpclient = HttpClients.createDefault();
    		HttpGet httpGet = new HttpGet(url);
    		httpGet.addHeader("Authorization","Bearer " + getAuthToken());
    		httpGet.addHeader("Accept", "application/json");
    		CloseableHttpResponse response1 = httpclient.execute(httpGet);
    		try {
    			System.out.println(response1.getStatusLine());
    			HttpEntity entity1 = response1.getEntity();
    			String data = convertStreamToString(entity1.getContent());
    			System.out.println(data);
    			EntityUtils.consume(entity1);
    		} finally {
    			response1.close();
    		}
    	
    	} catch (Exception e) {
    		e.printStackTrace();
    		return false;
    	}  	
    	
    	return true;
    	
    	/*
  public getCollectionMetadata = (collectionId:string, repository=RestConstants.HOME_REPOSITORY) : Observable<EduData.Collection> => {
    let query=this.connector.createUrl("collection/:version/collections/:repository/:collectionid",repository,[[":collectionid",collectionId]]);
    return this.connector.get(query,this.connector.getRequestOptions())
      .map((response: Response) => response.json());
  }
    	 */
    	
    	// *** 2. Set Property ***
    	
    	
    	// *** 3. Store Collection back to API ***
    	
    	/*
  public updateCollection = (collection:EduData.Collection) : Observable<void> => {

    var repo:string = RestConstants.HOME_REPOSITORY;
    if ((collection.ref.repo!=null) && (collection.ref.repo!="local")) repo = collection.ref.repo;

    let query:string = this.connector.createUrl("collection/:version/collections/:repository/:collectionid",repo,[[":collectionid",collection.ref.id]]);

    let body:string = JSON.stringify(collection);

    let options:RequestOptionsArgs = this.connector.getRequestOptions();
    options.headers.append('Accept', 'text/html');

    return this.connector.put(query, body, options).map((response: Response) => {});

  };
    	 */
    	
    }
	
	/*
	 * EXTRA DATA 
	 * handle the extra data like image and color for the collections to be crteated
	 */

	static Map<String,ExtraData> extraDataMap = null;
	static final Gson gson = new Gson();
	
	// tool method to load file as string
	static String readFile(String path) {
		try {
			byte[] encoded = Files.readAllBytes(Paths.get(path));
			return new String(encoded, "UTF-8");
		} catch (Exception e) {
			System.err.println("FAILED TO LOAD "+path+" --> EXIT");
			e.printStackTrace();
			System.exit(0);
			return null;
		}	
	}
	
	// one line in extra data file - add here fields if you extend it
	public class ExtraData {
		String id;
		String image;
		String color;
	}
		
	// loads extra data JSON file and maps it into a hash map
	public static Map<String,ExtraData> loadExtraDataFromFile(String filename) {
		
		// read file
		String extraData = readFile("./collectionData/"+filename);
		
		// convert JSON string to objects and fill up map  
		@SuppressWarnings("rawtypes")
		List data = gson.fromJson(extraData, List.class);
		Map<String,ExtraData> result = new HashMap<String,ExtraData>();
		for (Object entry : data) {
			ExtraData item = gson.fromJson(gson.toJson(entry),ExtraData.class);
			result.put(item.id.toUpperCase(), item);
		}
		return result;
	}
	
}
