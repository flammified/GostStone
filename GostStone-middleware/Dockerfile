FROM python:3

ADD main.py /home
ADD requirements.txt /home

RUN pip install -r /home/requirements.txt

RUN mkdir /logs/

CMD [ "python", "-u", "/home/main.py" ]
