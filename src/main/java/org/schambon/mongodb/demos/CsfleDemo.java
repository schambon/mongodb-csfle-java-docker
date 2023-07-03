package org.schambon.mongodb.demos;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.print.Doc;

import org.bson.BsonArray;
import org.bson.BsonBinary;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.javafaker.Faker;
import com.mongodb.AutoEncryptionSettings;
import com.mongodb.ClientEncryptionSettings;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.vault.DataKeyOptions;
import com.mongodb.client.vault.ClientEncryption;
import com.mongodb.client.vault.ClientEncryptions;

public class CsfleDemo {

    private static final Logger LOGGER = LoggerFactory.getLogger(CsfleDemo.class);

    public static void main(String[] args) throws Exception {
        
        String confString = Files.readString(Path.of(args[0]));
        var config = Document.parse(confString);

        var connectionString = config.getString("connectionString");
        var keyVaultDoc = (Document) config.get("keyvault");
        var keyVaultNamespace = keyVaultDoc.getString("db") + "." + keyVaultDoc.getString("coll");
        var dataDoc = (Document) config.get("data");

        String kmsProvider = "local";
        String path = config.getString("masterKeyPath");

        byte[] localMasterKeyRead = Files.readAllBytes(Path.of(path));
        
        Map<String, Map<String, Object>> kmsProviders = new HashMap<String, Map<String, Object>>() {{
            put("local", new HashMap<String, Object>() {{
                put("key", localMasterKeyRead);
            }});
        }};

        ClientEncryptionSettings clientEncryptionSettings = ClientEncryptionSettings.builder()
            .keyVaultMongoClientSettings(MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(connectionString))
                    .build())
            .keyVaultNamespace(keyVaultNamespace)
            .kmsProviders(kmsProviders)
            .build();

        MongoClient regularClient = MongoClients.create(connectionString);

        LOGGER.info("Dropping keyvault database...");
        regularClient.getDatabase(keyVaultDoc.getString("db")).drop();

        ClientEncryption clientEncryption = ClientEncryptions.create(clientEncryptionSettings);
        BsonBinary dataKeyId = clientEncryption.createDataKey(kmsProvider, new DataKeyOptions());
        String base64DataKeyId = Base64.getEncoder().encodeToString(dataKeyId.getData());

        LOGGER.info("Created keyvault with DEK id: " + base64DataKeyId);
        clientEncryption.close();

        LOGGER.info("Create data");

        var bred = new BufferedReader(new InputStreamReader(CsfleDemo.class.getClassLoader().getResourceAsStream("json_schema.json")));
        var schema = bred.lines().collect(Collectors.joining("\n"));
        bred.close();

        BsonDocument bsonSchema = BsonDocument.parse(schema);
        var keyIds = new BsonArray();
        keyIds.add(dataKeyId);
        bsonSchema.getDocument("encryptMetadata").put("keyId", keyIds);

        AutoEncryptionSettings autoEncryptionSettings = AutoEncryptionSettings.builder()
            .keyVaultNamespace(keyVaultNamespace)
            .kmsProviders(kmsProviders)
            .schemaMap(new HashMap<String, BsonDocument>() {{
                put(dataDoc.getString("db") + "." + dataDoc.get("coll"), bsonSchema);
            }})
            .build();

        MongoClientSettings settings = MongoClientSettings.builder()
            .applyConnectionString(new ConnectionString(connectionString))
            .autoEncryptionSettings(autoEncryptionSettings)
            .build();

        MongoClient secureClient = MongoClients.create(settings);
        var coll = secureClient.getDatabase(dataDoc.getString("db")).getCollection(dataDoc.getString("coll"));

        var faker = new Faker();
        var cc = faker.business().creditCardNumber();
        var doc = new Document()
            .append("_id", 0)
            .append("name", faker.name().fullName())
            .append("creditCard", cc)
            .append("lastCCDigits", cc.substring(cc.length() - 4, cc.length()));

        coll.insertOne(doc);
        LOGGER.info("Inserted document");

        var foundEncrypted = regularClient.getDatabase(dataDoc.getString("db")).getCollection(dataDoc.getString("coll")).find(Filters.eq("_id", 0)).first();
        LOGGER.info("Document read back without decryption: {}", foundEncrypted.toJson());

        var foundDecrypted = coll.find(Filters.eq("_id", 0)).first();
        LOGGER.info("Document read back with decryption: {}", foundDecrypted.toJson());
    }


}

