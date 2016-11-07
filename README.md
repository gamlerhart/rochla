# Project Rochla
Original goal: Learn Clojure =). Do something fun with it.

Gives a website, which hands our ready to use Windows boxes in a minute.
The gives you RDP access right on the website


# Run, via docker
Run a guacomole deamon: `docker run --name guacd -d glyptodon/guacd`.

Run the instance:

    docker run -d -p 80:8080 \
    --name web \
    --link guacd:guacd \
    --env AWS_ACCESS_KEY=<your-aws-access-key> \
    --env AWS_SECRET_KEY=<your-aws-secret-key> \
    --env AWS_ENDPOINT=<aws-end-point> \
    --env AWS_KEYNAME=<ssh-key-name> \
    --env AWS_AMI=<ami-for-Windows-Server-2016 \
    --env DOWNLOADS_URL="<url for the downloads>" \
    --env MAIN_URL="<url of the site/api. The windows boxed need to be able to reach these boxes>" \
    --env GUAC_HOST=gucd \
    --env GUAC_PORT=4822 \
    --env LOCATION="<nice location name>" \
    gamlerhart/rochla



## License

Distributed under the Mozilla Public License Version 2.0