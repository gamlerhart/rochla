FROM openjdk:8

RUN mkdir /rochla
ADD ./ /rochla/
RUN chmod ugo+x /rochla/bin/rochla-start.sh
CMD /rochla/bin/rochla-start.sh