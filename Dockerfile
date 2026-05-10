FROM payara/micro:6.2025.5-jdk17

ENV DEPLOY_DIR=/opt/payara/deployments
RUN mkdir ${PAYARA_HOME}/config
COPY --chown=payara:payara domain.xml ${PAYARA_HOME}/config/
COPY --chown=payara:payara postgresql.jar ${PAYARA_HOME}/config/
USER payara
WORKDIR ${PAYARA_HOME}
COPY artifact/auth-service.war ${DEPLOY_DIR}/

EXPOSE 8080
CMD ["--addlibs","/opt/payara/config/postgresql.jar","--deploymentdir","/opt/payara/deployments","--rootdir","/opt/payara/config","--domainConfig","/opt/payara/config/domain.xml"]