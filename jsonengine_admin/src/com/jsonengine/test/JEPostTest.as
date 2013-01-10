package com.jsonengine.test
{
	import com.adobe.serialization.json.JSON;
	import com.jsonengine.net.NetManager;
	
	import flash.net.URLVariables;
	
	import org.libspark.as3unit.assert.assertNotNull;
	import org.libspark.as3unit.assert.assertTrue;
	import org.libspark.as3unit.assert.async;
	import org.libspark.as3unit.test;
	
	use namespace org.libspark.as3unit.test;

	/**
	 * Tests POST method.
	 */
	public class JEPostTest
	{		
		test function testJsonStylePost():void {
			
			// encode it into JSON
			var params:URLVariables = new URLVariables();
			params._doc = JSON.encode(AllTests.betty);
			
			// put it to server
			NetManager.i.sendReq("/_je/test", params, "POST", async(function(result:Object):void {
				var resultObj:Object = JSON.decode(String(result));
				assertTrue(AllTests.compareUsers(AllTests.betty, resultObj), "all props should have the same values");
				assertNotNull(resultObj._docId, "_docId should be included");
				assertNotNull(resultObj._updatedAt, "_updatedAt should be included");
				AllTests.resultUser1 = resultObj;
			}));
		}
		
		test function testFormStylePost():void {
			
			// get the test user Betty as query parameters
			var params:URLVariables = AllTests.getBettyAsParams();
			
			// put it to server
			NetManager.i.sendReq("/_je/test", params, "POST", async(function(result:Object):void {
				var resultObj:Object = JSON.decode(String(result));
				assertTrue(AllTests.compareUsers(AllTests.betty, resultObj), "all props should have the same values");
				assertNotNull(resultObj._docId, "_docId should be included");
				assertNotNull(resultObj._updatedAt, "_updatedAt should be included");
				AllTests.resultUser2 = resultObj;
			}));
		}
	}
}