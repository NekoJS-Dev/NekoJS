# Fixed-source two-axis review

Fixed baseline:`5878d5187151e2302e2e0f15beb0da5d8519776c`; reviewed source:`859d7a2489a77d81b885071c911efae3b2d81006`. Command:`git diff 5878d518...859d7a24`; solecommit`859d7a24 fix: preserve valid Python syntax for Java-only member names`. The two committed files are PythonClassRenderer and PythonRendererTest. Current handoff-document changes are not part of this source review.

Two independent read-only fork reviews ran in parallel. Both read committed diff/caller facts/AGENTS/CONTEXT/coding. Neither changed files, ran tests/builds/Minecraft, created branches or committed. Supplied leadvalidation is context, not independently repeated acceptance.

## Standards

No demonstrated issue:0documentedbreaches,0concrete baseline-smell findings.

Identifier filtering is private/centralized, and method emission/counting/overload discovery share the ordinary-method rule. This matches the local shared-cause/narrow-helper constraints in coding15-18. Invalid original Java-only names are omitted, not renamed into invented host targets. Invalid Beanprojection retains legal actual getter/setter methods; TypeReflector marks the getterproperty and setterflag as expected. Valid Beanprojection/setter-only behavior stays unchanged outside the intended invalid-property fallback.

There is no runtimeclasslookup/permissions/owner/publichostcontract or Minecraftdependency change, consistent with AGENTS27-31. New comment is English and explains the omission policy; no unrelated refactor/golden changes. Test uses the public TypeReflector→renderer seam with real Java class/interface fixtures, invalid/phantomalias negatives and actuallegal accessor/neighbor controls, matching coding observable-behavior rules.

Coverage bounds: invalidfields/enumconstants/all-omitted-body cases are not independently pinned by this focused fixture, though predicateapplication/ellipsis fallback were inspected. No Pyright or general UnicodeXID guarantee is inferred. Lead4535tests/344liveAST are not independently certified by this static review.

## Spec

No demonstrated narrow-spec issue:0findings.

[Prior next-step spec](../ticket37-b7-candidate-validation/NEXT.md)requires: “Do not silently invent callable renamed host methods…keep emitted Python syntactically valid and preserve legal neighboring members.” Filtering invalidoriginal/effective names instead of manufacturing aliases implements that constraint. Legal methods and existingkeyword path stay unchanged. TypeReflector actually classifies get2DigitYearStart/set2DigitYearStart as the relevant getter/setter; shared ordinarypredicate emits those legal realcalltargets when the derivedproperty cannot be represented, with emission/count/import rules aligned. The public reflectedfixture checks these facts.

No missingnarrowrequirement or unaskedscope is demonstrated. Field/interfaceconstant/enum filtering applies the same declaration syntax rule; no runtimeJava/HostAccess/Point/owner/wire/savedcontract edits or publicJava removal. Constructor/parameter/class/symbol naming are not broadly rewritten. The authored contract explicitly does not promise completeUnicodeXID/NFKC/renamedIR validity; this is a documented residual bound, not proof of a new observed failure.

Independent review is source-only. AST/import PASS is not Pyright/allnode/multiplayer proof. TS3116diagnostics and residualperformance gates remain separate open work. This review does not approve publication, publicAPI deletion or all48tickets.

Summary: Standards0findings, Spec0findings; no worst demonstrated issue on either axis. All validationlimits remain recorded rather than waived.
