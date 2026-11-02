// Place in test_scripts and run /nekojs test. This does not run on server reload.
Test.section("Authoring example assertions");
Test.assertEquals("minecraft:stick", ["minecraft", "stick"].join(":"), "identifier formatting");
Test.assertTrue(["oak", "birch"].includes("oak"), "membership check");
Test.assertThrows(() => { throw new Error("Expected example failure"); }, "failure is observed");
Test.summary();
