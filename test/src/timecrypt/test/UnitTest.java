package timecrypt.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.BitSet;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import timecrypt.client.TimeCrypt;
import timecrypt.client.security.ECElGamalWrapper;
import timecrypt.client.security.OPEWrapper;
import timecrypt.client.security.OREWrapper;
import timecrypt.client.security.PaillierWrapper;
import timecrypt.client.security.Trapdoor;
import timecrypt.client.utils.Utility;

public class UnitTest {

    private final static String IP = "127.0.0.1";
    private final static int PORT = 8001;

    private static Trapdoor td;
    private final static double BF_FALSEPOSITIVE_PROBABILITY = 0.01;
    private final static int BF_EXPECTED_NUM_OF_TAGS = 16;

    private static OPEWrapper ope;
    private static OREWrapper ore;
    private static ECElGamalWrapper ecelgamal;
    private static PaillierWrapper paillier;
    
    private TimeCrypt client;

    @BeforeClass
    public static void setupClass() throws IOException, NoSuchAlgorithmException {
        paillier = new PaillierWrapper();
        td = new Trapdoor();
        ope = new OPEWrapper();
        ecelgamal = new ECElGamalWrapper();
        ore = new OREWrapper();
    }

    @Before
    public void setupClient() throws IOException {
        this.client = new TimeCrypt(IP, PORT);
        this.client.openConnection();
    }
    
    @After
    public void teardownClient() throws IOException {
        this.client.closeConnection();
    }

    @Test
    public void createStream() throws IOException {
        String streamID = client.createStream(2, "{ 'sum': true, 'min': true, 'max': true, 'count': true, 'tags': true }", paillier.getPublicKey(), null);
        assertNotNull(streamID);
    }

    @Test
    public void insert() throws IOException, InvalidKeyException, NoSuchAlgorithmException {
        String streamID = client.createStream(2, "{ 'sum': true, 'min': true, 'max': true, 'count': true, 'tags': true }", paillier.getPublicKey(), null);
        assertNotNull(streamID);

        for (int i = 1; i < 16; i += 2) {
			long from = i;
			long to = i+1;
			BigInteger sum = paillier.encrypt(BigInteger.valueOf(1));
            BigInteger count = paillier.encrypt(BigInteger.valueOf(1));

            String keyAndData = String.format("%s-%s", from, to); // assume encrypted
            
            String tags = td.getFilter("test" + keyAndData, BF_FALSEPOSITIVE_PROBABILITY, BF_EXPECTED_NUM_OF_TAGS);
            
			BigInteger min = ope.encrypt(BigInteger.valueOf(from));
			BigInteger max = ope.encrypt(BigInteger.valueOf(to));

            boolean res = client.insert(streamID, keyAndData, keyAndData.getBytes(), getMetadataJson(from, to, sum, count, min, max, null, null, tags));
            assertEquals(true, res);
        }
    }

    @Test
    public void getStatistics() throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        // Create stream
        String streamID = client.createStream(2, "{ 'sum': true, 'min': true, 'max': true, 'count': true, 'tags': true }", paillier.getPublicKey(), null);
        assertNotNull(streamID);

        // Perform insert
        for (int i = 1; i < 16; i += 2) {
			long from = i;
			long to = i+1;
			BigInteger sum = paillier.encrypt(BigInteger.valueOf(1));
            BigInteger count = paillier.encrypt(BigInteger.valueOf(1));
            String keyAndData = String.format("%s-%s", from, to);
            String tags = td.getFilter("test" + keyAndData, BF_FALSEPOSITIVE_PROBABILITY, BF_EXPECTED_NUM_OF_TAGS);
			BigInteger min = ope.encrypt(BigInteger.valueOf(from));
			BigInteger max = ope.encrypt(BigInteger.valueOf(to));
            boolean res = client.insert(streamID, keyAndData, keyAndData.getBytes(), getMetadataJson(from, to, sum, count, min, max, null, null, tags));
            assertEquals(true, res);
        }

        // Retrieve statistics
        long from = 7;
		long to = 12;
        String metadataResult = client.getStatistics(streamID, from, to);
        
		JsonParser parser = new JsonParser();
		JsonObject jObj = parser.parse(metadataResult).getAsJsonObject();
		BigInteger sum = jObj.get("sum").getAsBigInteger();
		BigInteger count = jObj.get("count").getAsBigInteger();
		BigInteger min = jObj.get("min").getAsBigInteger();
        BigInteger max = jObj.get("max").getAsBigInteger();
        JsonArray tags = jObj.get("tags").getAsJsonArray();
        
        assertEquals(BigInteger.valueOf(3), paillier.decrypt(sum));
        assertEquals(BigInteger.valueOf(3), paillier.decrypt(count));
        assertEquals(BigInteger.valueOf(7), ope.decrypt(min));
        assertEquals(BigInteger.valueOf(12), ope.decrypt(max));
        
        BitSet bs = Utility.unmarshalBitSet(tags);
        assertEquals(true, td.containsTag("test7-8", bs, BF_FALSEPOSITIVE_PROBABILITY, BF_EXPECTED_NUM_OF_TAGS));
        assertEquals(true, td.containsTag("test11-12", bs, BF_FALSEPOSITIVE_PROBABILITY, BF_EXPECTED_NUM_OF_TAGS));
        assertEquals(false, td.containsTag("test8-9", bs, BF_FALSEPOSITIVE_PROBABILITY, BF_EXPECTED_NUM_OF_TAGS));
    }

    @Test
    public void ECELGamalSum() throws IOException {
        String streamID = client.createStream(2, "{ 'sum': true, 'algorithms': { 'sum': 'ecelgamal' } }", null, null);
        assertNotNull(streamID);

        // Perform insert
        for (int i = 1; i < 16; i += 2) {
			long from = i;
            long to = i+1;
            
			String sum = ecelgamal.encryptAndEncode(BigInteger.valueOf(1));
            String keyAndData = String.format("%s-%s", from, to);
            String md = String.format("{ 'from': %s, 'to': %s, 'sum': '%s' }", from, to, sum);
            boolean res = client.insert(streamID, keyAndData, keyAndData.getBytes(), md);
            assertEquals(true, res);
        }

        long from = 7;
		long to = 12;
        String metadataResult = client.getStatistics(streamID, from, to);
        
		JsonParser parser = new JsonParser();
		JsonObject jObj = parser.parse(metadataResult).getAsJsonObject();
        String sum = jObj.get("sum").getAsString();
        
        assertEquals(BigInteger.valueOf(3), ecelgamal.decodeAndDecrypt(sum));
    }

    @Test
    public void OREMinMax() throws Exception {
        String streamID = client.createStream(2, "{ 'min': true, 'max': true, 'algorithms': { 'min': 'ore', 'max': 'ore' } }", null, null);
        assertNotNull(streamID);

        // Perform insert
        for (int i = 1; i < 16; i += 2) {
			long from = i;
            long to = i+1;

			String min = ore.encryptAndEncode(BigInteger.valueOf(from)); 
			String max =  ore.encryptAndEncode(BigInteger.valueOf(to));
            String keyAndData = String.format("%s-%s", from, to);
            String md = String.format("{ 'from': %s, 'to': %s, 'min': '%s', 'max': '%s' }", from, to, min, max);
            boolean res = client.insert(streamID, keyAndData, keyAndData.getBytes(), md);
            assertEquals(true, res);
        }

        long from = 7;
		long to = 12;
        String metadataResult = client.getStatistics(streamID, from, to);
        
		JsonParser parser = new JsonParser();
		JsonObject jObj = parser.parse(metadataResult).getAsJsonObject();
		String min = jObj.get("min").getAsString();
        String max = jObj.get("max").getAsString();
        
        assertEquals(BigInteger.valueOf(7), ore.decodeAndDecrypt(min));
        assertEquals(BigInteger.valueOf(12), ore.decodeAndDecrypt(max));
    }

    @Test
    public void getRange() throws IOException, InvalidKeyException, NoSuchAlgorithmException {
        // Create stream
        String streamID = client.createStream(2, "{ 'sum': true, 'min': true, 'max': true, 'count': true, 'tags': true }", paillier.getPublicKey(), null);
        assertNotNull(streamID);

        // Perform insert
        for (int i = 1; i < 16; i += 2) {
			long from = i;
			long to = i+1;
			BigInteger sum = paillier.encrypt(BigInteger.valueOf(1));
            BigInteger count = paillier.encrypt(BigInteger.valueOf(1));
            String keyAndData = String.format("%s-%s", from, to);
            String tags = td.getFilter("test" + keyAndData, BF_FALSEPOSITIVE_PROBABILITY, BF_EXPECTED_NUM_OF_TAGS);
			BigInteger min = ope.encrypt(BigInteger.valueOf(from));
			BigInteger max = ope.encrypt(BigInteger.valueOf(to));
            boolean res = client.insert(streamID, keyAndData, keyAndData.getBytes(), getMetadataJson(from, to, sum, count, min, max, null, null, tags));
            assertEquals(true, res);
        }

        // Retrieve a range
		long from = 2;
		long to = 7;
        List<byte[]> retrievedRange = client.getRange(streamID, from, to);
        
        int i = 1;
        for (byte[] data : retrievedRange) {
            assertEquals(String.format("%s-%s", i, i+1), new String(data));
            i += 2;
        }
    }

    @Test
    public void firstLastChunkEntry() throws IOException, InvalidKeyException, NoSuchAlgorithmException {
        // Create stream
        String streamID = client.createStream(2, "{ 'sum': true, 'min': true, 'max': true, 'count': true, 'first': true, 'last': true, 'tags': true }", paillier.getPublicKey(), null);
        assertNotNull(streamID);

        // Perform insert
        for (int i = 1; i < 16; i += 2) {
			long from = i;
			long to = i+1;
			BigInteger sum = paillier.encrypt(BigInteger.valueOf(1));
            BigInteger count = paillier.encrypt(BigInteger.valueOf(1));
            String keyAndData = String.format("%s-%s", from, to);
            String tags = td.getFilter("test" + keyAndData, BF_FALSEPOSITIVE_PROBABILITY, BF_EXPECTED_NUM_OF_TAGS);
			BigInteger min = ope.encrypt(BigInteger.valueOf(from));
            BigInteger max = ope.encrypt(BigInteger.valueOf(to));
            BigInteger first = paillier.encrypt(new BigInteger(String.valueOf(from)));
            BigInteger last = paillier.encrypt(new BigInteger(String.valueOf(to)));
            boolean res = client.insert(streamID, keyAndData, keyAndData.getBytes(), getMetadataJson(from, to, sum, count, min, max, first, last, tags));
            assertEquals(true, res);
        }

        // Retrieve values of the first and last entry of the range
        long from = 7;
		long to = 12;
        String metadataResult = client.getStatistics(streamID, from, to);
        
		JsonParser parser = new JsonParser();
		JsonObject jObj = parser.parse(metadataResult).getAsJsonObject();
		BigInteger first = jObj.get("first").getAsBigInteger();
        BigInteger last = jObj.get("last").getAsBigInteger();
        
        System.out.println(paillier.decrypt(first));
        System.out.println(paillier.decrypt(last));
        
        assertEquals(BigInteger.valueOf(from), paillier.decrypt(first));
        assertEquals(BigInteger.valueOf(to), paillier.decrypt(last));
    }

    private String getMetadataJson(long from, long to, BigInteger sum, BigInteger count, BigInteger min, BigInteger max, BigInteger first, BigInteger last, String tags) {
        return String.format("{ 'from': %s, 'to': %s, 'sum': %s, 'count': %s, 'min': %s, 'max': %s, 'first': %s, 'last': %s, 'tags': %s }", from, to, sum, count, min, max, first, last, tags);
    }
}