describe('Services API Resource', function () {
    var ServicesResource, $httpBackend;

    beforeEach(module('adminNg.services'));
    beforeEach(module('adminNg.resources'));
    beforeEach(module('ngResource'));

    beforeEach(inject(function (_$httpBackend_, _ServicesResource_) {
        $httpBackend  = _$httpBackend_;
        ServicesResource = _ServicesResource_;
    }));

    describe('#query', function () {
        var sampleJSON = {
            'limit': 'Some(0)',
            'total': '39',
            'results': [
                {
                    'host': 'http://mh-allinone.localdomain',
                    'queued': '0',
                    'status': 'NORMAL',
                    'name': 'org.opencastproject.userdirectory.roles',
                    'meanQueueTime': '1',
                    'running': '0',
                    'meanRunTime': '1',
                    'completed': '1'
                },
                {
                    'host': 'http://mh-allinone.localdomain',
                    'queued': '0',
                    'status': 'OFFLINE',
                    'name': 'org.opencastproject.userdirectory.users',
                    'meanQueueTime': '1',
                    'running': '0',
                    'meanRunTime': '1',
                    'completed': '1'
                }
            ],
            'offset': 'Some(0)'
        };

        var sampleSimpleJSON = {
            'limit': 'Some(0)',
            'total': '39',
            'results': [
                {
                    'host': 'http://mh-allinone.localdomain',
                    'queued': '0',
                    'status': 'NORMAL',
                    'name': 'org.opencastproject.userdirectory.roles',
                    'meanQueueTime': '0',
                    'running': '0',
                    'meanRunTime': '0',
                    'completed': '0'
                },
                {
                    'host': 'http://mh-allinone.localdomain',
                    'queued': '0',
                    'status': 'OFFLINE',
                    'name': 'org.opencastproject.userdirectory.users',
                    'meanQueueTime': '0',
                    'running': '0',
                    'meanRunTime': '0',
                    'completed': '0'
                }
            ],
            'offset': 'Some(0)'
        };
        it('calls the services service', function () {
            $httpBackend.expectGET('/admin-ng/services/services.json?full=false').respond(JSON.stringify(sampleSimpleJSON));
            ServicesResource.query();
            $httpBackend.flush();
        });

        it('flattens the JSON data received', function () {
            $httpBackend.whenGET('/admin-ng/services/services.json?full=false').respond(JSON.stringify(sampleSimpleJSON));
            var data = ServicesResource.query();
            $httpBackend.flush();
            expect(data.rows.length).toBe(2);
        });

        it('handles full=true', function () {
            $httpBackend.whenGET('/admin-ng/services/services.json?full=true').respond(JSON.stringify(sampleJSON));
            var data = ServicesResource.query({full: 'true'});
            $httpBackend.flush();
            expect(data.rows.length).toBe(2);
        });
    });
});
