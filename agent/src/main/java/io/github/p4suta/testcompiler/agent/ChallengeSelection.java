package io.github.p4suta.testcompiler.agent;

record ChallengeSelection(String planId, String owner, String method, String descriptor, String operator, int line) {
    static ChallengeSelection fromSystemProperties() {
        return new ChallengeSelection(
                System.getProperty("testcompiler.plan.id", ""),
                System.getProperty("testcompiler.plan.owner", ""),
                System.getProperty("testcompiler.plan.method", ""),
                System.getProperty("testcompiler.plan.descriptor", ""),
                System.getProperty("testcompiler.plan.operator", ""),
                Integer.getInteger("testcompiler.plan.line", 0));
    }

    boolean matches(
            String candidateOwner, String candidateMethod, String candidateDescriptor, String candidateOperator, int candidateLine) {
        return !planId.isBlank()
                && owner.equals(candidateOwner)
                && method.equals(candidateMethod)
                && descriptor.equals(candidateDescriptor)
                && operator.equals(candidateOperator)
                && (line == 0 || line == candidateLine);
    }
}
