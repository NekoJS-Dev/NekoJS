# Inspect printed values externally; this focused probe does not assert success.
def issue69_python_entry():
    return 7

print('ISSUE69_PY_LOAD')
issue69_python_result = issue69_python_entry()
issue69_python_text_empty = Text.of('issue69').isEmpty()
print('ISSUE69_PY_RESULT', issue69_python_result)
print('ISSUE69_PY_TEXT_EMPTY', issue69_python_text_empty)
print('ISSUE69_PY_EXECUTED')
